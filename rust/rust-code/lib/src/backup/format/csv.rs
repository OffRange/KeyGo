use crate::backup::{Backup, BackupError, Login, Vault};
use crate::totp::is_valid_totp_secret;
use crate::url::sanitize_to_https_url;
use csv::StringRecord;
use email_address::Options;

#[derive(Debug, Default, PartialEq, Eq)]
pub struct ColumnMapping {
    pub title: Option<usize>,
    pub url: Option<usize>,
    pub username: Option<usize>,
    pub password: Option<usize>,
    pub notes: Option<usize>,
    pub totp: Option<usize>,
}

#[derive(Debug, PartialEq, Eq)]
pub enum Confidence {
    High,
    Medium,
    Low,
}

impl Confidence {
    fn from_score(score: u32) -> Confidence {
        if score >= 90 {
            Confidence::High
        } else if score >= 45 {
            Confidence::Medium
        } else {
            Confidence::Low
        }
    }
}

#[derive(Debug, Default, PartialEq, Eq)]
pub struct FieldConfidence {
    pub title: Option<Confidence>,
    pub url: Option<Confidence>,
    pub username: Option<Confidence>,
    pub password: Option<Confidence>,
    pub notes: Option<Confidence>,
    pub totp: Option<Confidence>,
}

#[derive(Debug, PartialEq, Eq)]
pub struct CsvColumn {
    pub index: u32,
    pub header: String,
    pub sample_values: Vec<String>,
}

pub struct CsvAnalysis {
    pub columns: Vec<CsvColumn>,
    pub suggested: ColumnMapping,
    pub confidence: FieldConfidence,
}

#[derive(Debug, Default, PartialEq, Eq)]
pub struct ImportReport {
    pub imported: u32,
    pub skipped: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Field {
    Title,
    Url,
    Username,
    Password,
    Notes,
    Totp,
}

const ALL_FIELDS: [Field; 6] = [
    Field::Title,
    Field::Url,
    Field::Username,
    Field::Password,
    Field::Notes,
    Field::Totp,
];

impl Field {
    /// Borrow this field's value from a login, or `None` for an absent optional.
    /// The title is always present.
    fn read(self, login: &Login) -> Option<&str> {
        match self {
            Field::Title => Some(login.title.as_str()),
            Field::Url => login.websites.first().map(String::as_str),
            Field::Username => login.username.as_deref(),
            Field::Password => login.password.as_deref(),
            Field::Notes => login.notes.as_deref(),
            Field::Totp => login.totp_secret.as_deref(),
        }
    }
}

impl ColumnMapping {
    fn set(&mut self, field: Field, idx: usize) {
        match field {
            Field::Title => self.title = Some(idx),
            Field::Url => self.url = Some(idx),
            Field::Username => self.username = Some(idx),
            Field::Password => self.password = Some(idx),
            Field::Notes => self.notes = Some(idx),
            Field::Totp => self.totp = Some(idx),
        }
    }
}

impl FieldConfidence {
    fn set(&mut self, field: Field, conf: Confidence) {
        match field {
            Field::Title => self.title = Some(conf),
            Field::Url => self.url = Some(conf),
            Field::Username => self.username = Some(conf),
            Field::Password => self.password = Some(conf),
            Field::Notes => self.notes = Some(conf),
            Field::Totp => self.totp = Some(conf),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExportPreset {
    /// KeyGo's own layout: every field, with headers that [`analyze`] maps back
    /// at High confidence, so a backup round-trips through [`import`].
    KeyGo,
    /// A browser-importable layout (Chrome, Edge, ...): `name,url,username,password,note`
    /// and no TOTP column.
    Browser,
}

impl ExportPreset {
    fn columns(self) -> &'static [(&'static str, Field)] {
        use Field::*;
        match self {
            ExportPreset::KeyGo => &[
                ("title", Title),
                ("url", Url),
                ("username", Username),
                ("password", Password),
                ("notes", Notes),
                ("totp", Totp),
            ],
            ExportPreset::Browser => &[
                ("name", Title),
                ("url", Url),
                ("username", Username),
                ("password", Password),
                ("note", Notes),
            ],
        }
    }
}

const DELIMITERS: [u8; 4] = *b",;\t|";

/// Strip a leading UTF-8 BOM, if present.
fn strip_bom(data: &str) -> &str {
    data.strip_prefix('\u{feff}').unwrap_or(data)
}

fn detect_delimiter(data: &str) -> u8 {
    let mut best = b',';
    let mut best_score = -1i64;

    for &delim in &DELIMITERS {
        let mut rdr = csv::ReaderBuilder::new()
            .delimiter(delim)
            .has_headers(false) // Treat all lines as data for counting
            .flexible(true)
            .from_reader(data.as_bytes());

        let mut columns = Vec::with_capacity(5);
        for result in rdr.records().take(5) {
            match result {
                Ok(record) => columns.push(record.len()),
                Err(_) => break, // If parsing fails wildly, abandon this delimiter
            }
        }
        if columns.is_empty() {
            continue;
        }

        let max = *columns.iter().max().unwrap_or(&1);
        if max <= 1 {
            continue;
        }

        let consistent = columns.iter().all(|&c| c == columns[0]);
        let score = (consistent as i64) * 1000 + max as i64;
        if score > best_score {
            best_score = score;
            best = delim;
        }
    }
    best
}

fn build_reader(data: &str) -> csv::Reader<&[u8]> {
    csv::ReaderBuilder::new()
        .delimiter(detect_delimiter(data))
        .has_headers(true)
        .flexible(true)
        .from_reader(data.as_bytes())
}

fn looks_like_email(s: &str) -> bool {
    email_address::EmailAddress::parse_with_options(s, Options::default().with_required_tld())
        .is_ok()
}

fn looks_like_url(s: &str) -> bool {
    !looks_like_email(s) && sanitize_to_https_url(s).is_ok()
}

fn looks_like_totp(s: &str) -> bool {
    let s = s.trim();
    if s.is_empty() {
        return false;
    }
    if s.to_ascii_lowercase().starts_with("otpauth://") {
        return true;
    }

    is_valid_totp_secret(s) && s.len() >= 16
}

const HEADER_EXACT: u32 = 100;
const HEADER_CONTAINS: u32 = 30;
const VALUE_MAX: u32 = 50;
const MIN_SCORE: u32 = 25;

/// Lowercase a header and collapse every run of non-alphanumeric characters
/// (spaces, `_`, `-`, `.`, `/`, ...) into a single space, trimming the ends. This
/// makes `login_uri`, `Login-URI`, `login.uri`, and `Login URI` all compare
/// equal, so a header matches the keyword tables regardless of separator style.
fn normalize_header(header: &str) -> String {
    let mut out = String::with_capacity(header.len());
    let mut pending_space = false;
    for c in header.chars().flat_map(char::to_lowercase) {
        if c.is_alphanumeric() {
            if pending_space && !out.is_empty() {
                out.push(' ');
            }
            pending_space = false;
            out.push(c);
        } else {
            pending_space = true;
        }
    }
    out
}

/// Score a single field against an already-[`normalize_header`]d header.
fn header_score(field: Field, header: &str) -> u32 {
    let (exact, contains): (&[&str], &[&str]) = match field {
        Field::Title => (
            &[
                "title",
                "name",
                "account",
                "account name",
                "item",
                "entry",
                "display name",
                "service",
            ],
            &["title", "name"],
        ),
        Field::Url => (
            &[
                "url",
                "uri",
                "website",
                "web site",
                "web",
                "site",
                "link",
                "host",
                "hostname",
                "domain",
                "login uri",
                "login url",
            ],
            &[
                "url", "uri", "website", "web", "site", "host", "domain", "link",
            ],
        ),
        Field::Username => (
            &[
                "username",
                "user name",
                "user",
                "user id",
                "userid",
                "login",
                "login name",
                "login username",
                "email",
                "e mail",
            ],
            &["user", "login", "email"],
        ),
        Field::Password => (
            &[
                "password",
                "pass",
                "pwd",
                "passwd",
                "secret",
                "login password",
            ],
            &["password", "passwd", "pwd"],
        ),
        Field::Notes => (
            &[
                "notes",
                "note",
                "comment",
                "comments",
                "description",
                "extra",
                "memo",
            ],
            &["note", "comment", "description", "memo"],
        ),
        Field::Totp => (
            &[
                "totp",
                "otp",
                "otpauth",
                "2fa",
                "two factor",
                "twofactor",
                "authenticator",
                "seed",
                "login totp",
            ],
            &["totp", "otp", "2fa", "authenticator"],
        ),
    };
    if exact.contains(&header) {
        HEADER_EXACT
    } else if contains.iter().any(|k| header.contains(k)) {
        HEADER_CONTAINS
    } else {
        0
    }
}

struct Profile {
    url: f32,
    email: f32,
    totp: f32,
}

impl Profile {
    fn score(&self, field: Field) -> u32 {
        let frac = match field {
            Field::Url => self.url,
            Field::Username => self.email,
            Field::Totp => self.totp,
            _ => 0.0,
        };
        (frac * VALUE_MAX as f32) as u32
    }
}

fn profile_column(samples: &[StringRecord], col: usize) -> Profile {
    let mut total = 0u32;
    let mut url = 0u32;
    let mut email = 0u32;
    let mut totp = 0u32;
    for row in samples {
        if let Some(cell) = row.get(col) {
            let cell = cell.trim();
            if cell.is_empty() {
                continue;
            }
            total += 1;

            // url and email are mutually exclusive: looks_like_url already
            // rejects anything that parses as an email.
            if looks_like_url(cell) {
                url += 1;
            } else if looks_like_email(cell) {
                email += 1;
            }

            if looks_like_totp(cell) {
                totp += 1;
            }
        }
    }
    let t = total.max(1) as f32;
    Profile {
        url: url as f32 / t,
        email: email as f32 / t,
        totp: totp as f32 / t,
    }
}

/// Greedy best-fit assignment: each column maps to at most one field and each
/// field to at most one column, taking the highest scores first. Ties resolve by
/// field declaration order, then column index, for determinism.
fn build_mapping(headers: &[String], samples: &[StringRecord]) -> (ColumnMapping, FieldConfidence) {
    let profiles: Vec<Profile> = (0..headers.len())
        .map(|c| profile_column(samples, c))
        .collect();

    let mut candidates: Vec<(u32, usize, usize)> = Vec::new(); // (score, field_idx, col)
    for (col, header) in headers.iter().enumerate() {
        let h = normalize_header(header);

        for (field_idx, field) in ALL_FIELDS.into_iter().enumerate() {
            let score = header_score(field, &h) + profiles[col].score(field);
            if score >= MIN_SCORE {
                candidates.push((score, field_idx, col));
            }
        }
    }
    candidates.sort_by(|a, b| b.0.cmp(&a.0).then(a.1.cmp(&b.1)).then(a.2.cmp(&b.2)));

    let mut mapping = ColumnMapping::default();
    let mut confidence = FieldConfidence::default();
    let mut used_cols = vec![false; headers.len()];
    let mut used_fields = [false; ALL_FIELDS.len()];

    for (score, field_idx, col) in candidates {
        if used_cols[col] || used_fields[field_idx] {
            continue;
        }
        let field = ALL_FIELDS[field_idx];
        mapping.set(field, col);
        confidence.set(field, Confidence::from_score(score));
        used_cols[col] = true;
        used_fields[field_idx] = true;
    }

    (mapping, confidence)
}

/// must be `>= DISPLAY_SAMPLES`.
const SAMPLE_ROWS: usize = 10;
const DISPLAY_SAMPLES: usize = 5;

/// Inspect a CSV and return a review-ready analysis: the columns (with a few
/// sample values each), a suggested editable mapping, and per-field confidence.
///
/// Column types are inferred from the header names and from the first
/// [`SAMPLE_ROWS`] parseable data rows (malformed rows are skipped). Only those
/// rows are read, so the result is deterministic and the cost is independent of
/// file size.
pub fn analyze(data: &str) -> Result<CsvAnalysis, BackupError> {
    let data = strip_bom(data);
    if data.trim().is_empty() {
        return Err(BackupError::EmptyCsv);
    }

    let mut reader = build_reader(data);
    let headers: Vec<String> = reader
        .headers()
        .map_err(|e| BackupError::Csv(e.to_string()))?
        .iter()
        .map(|h| h.trim().to_string())
        .collect();
    if headers.is_empty() {
        return Err(BackupError::EmptyCsv);
    }

    // Read at most the first SAMPLE_ROWS parseable rows, skipping malformed
    // ones. `take` keeps this lazy, so a large file is never fully scanned.
    let samples: Vec<StringRecord> = reader
        .records()
        .filter_map(Result::ok)
        .take(SAMPLE_ROWS)
        .collect();

    let (suggested, confidence) = build_mapping(&headers, &samples);

    let columns = headers
        .into_iter()
        .enumerate()
        .map(|(i, header)| CsvColumn {
            index: i as u32,
            header,
            sample_values: samples
                .iter()
                .filter_map(|row| row.get(i))
                .map(|s| s.trim())
                .filter(|s| !s.is_empty())
                .take(DISPLAY_SAMPLES)
                .map(|s| s.to_string())
                .collect(),
        })
        .collect();

    Ok(CsvAnalysis {
        columns,
        suggested,
        confidence,
    })
}

pub fn import(data: &str, mapping: &ColumnMapping) -> Result<(Backup, ImportReport), BackupError> {
    let data = strip_bom(data);
    if data.trim().is_empty() {
        return Err(BackupError::EmptyCsv);
    }

    let mut reader = build_reader(data);

    let mut logins = Vec::new();
    let mut report = ImportReport::default();

    for record in reader.records() {
        let record = match record {
            Ok(rec) => rec,
            Err(_) => {
                report.skipped += 1;
                continue;
            }
        };

        let field = |col: Option<usize>| -> Option<String> {
            col.and_then(|i| record.get(i))
                .map(str::trim)
                .filter(|s| !s.is_empty())
                .map(str::to_owned)
        };

        let title = field(mapping.title);
        let url = field(mapping.url);
        let username = field(mapping.username);
        let password = field(mapping.password);
        let notes = field(mapping.notes);
        let totp = field(mapping.totp);

        if title.is_none()
            && url.is_none()
            && username.is_none()
            && password.is_none()
            && notes.is_none()
            && totp.is_none()
        {
            report.skipped += 1;
            continue;
        }

        report.imported += 1;
        logins.push(Login {
            title: title
                .or_else(|| url.clone())
                .or_else(|| username.clone())
                .unwrap_or_else(|| "Untitled".to_owned()),
            username,
            password,
            totp_secret: totp,
            websites: url.into_iter().collect(),
            notes,
            ..Default::default()
        });
    }

    let backup = Backup {
        vaults: vec![Vault {
            name: "CSV Import".to_string(),
            logins,
            ..Default::default()
        }],
    };
    Ok((backup, report))
}

pub fn export(backup: &Backup, preset: ExportPreset) -> Result<String, BackupError> {
    let columns = preset.columns();
    let mut writer = csv::Writer::from_writer(Vec::new());

    let csv_err = |e: csv::Error| BackupError::Csv(e.to_string());
    writer
        .write_record(columns.iter().map(|&(header, _)| header))
        .map_err(csv_err)?;

    for login in backup.vaults.iter().flat_map(|v| &v.logins) {
        let row = columns
            .iter()
            .map(|(_, field)| field.read(login).unwrap_or_default());
        writer.write_record(row).map_err(csv_err)?;
    }

    let bytes = writer
        .into_inner()
        .map_err(|e| BackupError::Csv(e.to_string()))?;
    String::from_utf8(bytes).map_err(|e| BackupError::Csv(e.to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::backup::BackupError;

    fn rows(data: &[&[&str]]) -> Vec<StringRecord> {
        data.iter()
            .map(|r| r.iter().map(|c| c.to_string()).collect())
            .collect()
    }

    fn hdrs(h: &[&str]) -> Vec<String> {
        h.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn maps_chrome_headers() {
        let headers = hdrs(&["name", "url", "username", "password", "note"]);
        let (m, c) = build_mapping(&headers, &[]);
        assert_eq!(m.title, Some(0));
        assert_eq!(m.url, Some(1));
        assert_eq!(m.username, Some(2));
        assert_eq!(m.password, Some(3));
        assert_eq!(m.notes, Some(4));
        assert_eq!(c.password, Some(Confidence::High)); // exact header match
    }

    #[test]
    fn maps_bitwarden_headers() {
        let headers = hdrs(&[
            "folder",
            "favorite",
            "type",
            "name",
            "notes",
            "fields",
            "reprompt",
            "login_uri",
            "login_username",
            "login_password",
            "login_totp",
        ]);
        let (m, _) = build_mapping(&headers, &[]);
        assert_eq!(m.title, Some(3));
        assert_eq!(m.notes, Some(4));
        assert_eq!(m.url, Some(7));
        assert_eq!(m.username, Some(8));
        assert_eq!(m.password, Some(9));
        assert_eq!(m.totp, Some(10));
    }

    #[test]
    fn maps_keepass_headers_case_insensitively() {
        let headers = hdrs(&["Account", "Login Name", "Password", "Web Site", "Comments"]);
        let (m, _) = build_mapping(&headers, &[]);
        assert_eq!(m.title, Some(0));
        assert_eq!(m.username, Some(1));
        assert_eq!(m.password, Some(2));
        assert_eq!(m.url, Some(3));
        assert_eq!(m.notes, Some(4));
    }

    #[test]
    fn maps_ms_headers_titlecase() {
        let headers = hdrs(&["Name", "Url", "Username", "Password", "Notes"]);
        let (m, _) = build_mapping(&headers, &[]);
        assert_eq!(m.title, Some(0));
        assert_eq!(m.url, Some(1));
        assert_eq!(m.username, Some(2));
        assert_eq!(m.password, Some(3));
        assert_eq!(m.notes, Some(4));
    }

    #[test]
    fn value_sniffing_drives_vague_headers() {
        // Columns 1 and 2 have meaningless headers; only their values reveal them.
        let headers = hdrs(&["name", "field_a", "field_b"]);
        let samples = rows(&[
            &["Site One", "alice@example.com", "https://one.example"],
            &["Site Two", "bob@example.com", "https://two.example"],
        ]);
        let (m, c) = build_mapping(&headers, &samples);
        assert_eq!(m.title, Some(0));
        assert_eq!(m.username, Some(1)); // emails
        assert_eq!(m.url, Some(2)); // urls
        assert_eq!(c.username, Some(Confidence::Medium)); // value-only match
    }

    #[test]
    fn unmatched_columns_stay_unmapped() {
        let headers = hdrs(&["folder", "favorite", "reprompt"]);
        let (m, _) = build_mapping(&headers, &[]);
        assert_eq!(m, ColumnMapping::default());
    }

    #[test]
    fn header_separators_normalize_to_exact_match() {
        // Underscore, hyphen, dot, mixed case, and repeated spaces all normalize
        // to one exact-match phrase and earn High (not merely "contains")
        // confidence.
        for h in [
            "login_username",
            "login-username",
            "login.username",
            "Login Username",
            "LOGIN   USERNAME",
        ] {
            let headers = hdrs(&[h, "login-password"]);
            let (m, c) = build_mapping(&headers, &[]);
            assert_eq!(m.username, Some(0), "{h:?} should map to username");
            assert_eq!(m.password, Some(1), "{h:?} row: password should map");
            assert_eq!(
                c.username,
                Some(Confidence::High),
                "{h:?} should be an exact match"
            );
        }
    }

    #[test]
    fn normalize_header_collapses_separators() {
        assert_eq!(normalize_header("  Login_URI "), "login uri");
        assert_eq!(normalize_header("E-Mail"), "e mail");
        assert_eq!(normalize_header("web..site"), "web site");
        assert_eq!(normalize_header("___"), "");
    }

    #[test]
    fn analyze_samples_only_leading_rows_deterministically() {
        // 12 data rows, but only the first SAMPLE_ROWS feed the analysis and the
        // result is stable across runs (no randomness).
        let mut csv = String::from("name,login_uri,login_username\n");
        for i in 0..12 {
            csv.push_str(&format!(
                "Site {i},https://s{i}.example,user{i}@example.com\n"
            ));
        }
        let a1 = analyze(&csv).unwrap();
        let a2 = analyze(&csv).unwrap();
        assert_eq!(a1.columns[0].sample_values, a2.columns[0].sample_values);
        // Display samples are the first rows, in file order.
        assert_eq!(
            a1.columns[0].sample_values,
            vec!["Site 0", "Site 1", "Site 2", "Site 3", "Site 4"]
        );
        // Separator-styled headers map (and value sniffing agrees).
        assert_eq!(a1.suggested.url, Some(1));
        assert_eq!(a1.suggested.username, Some(2));
        assert_eq!(a1.confidence.url, Some(Confidence::High));
    }

    #[test]
    fn detects_comma_semicolon_tab() {
        assert_eq!(detect_delimiter("a,b,c\n1,2,3"), b',');
        assert_eq!(detect_delimiter("a;b;c\n1;2;3"), b';');
        assert_eq!(detect_delimiter("a\tb\tc\n1\t2\t3"), b'\t');
    }

    #[test]
    fn semicolon_wins_when_commas_only_inside_fields() {
        // header has no commas; a data cell does. The semicolon count is
        // consistent across lines, so it must win over the ragged comma count.
        let data = "name;url;notes\nSite;https://x.com;\"a, b, c\"";
        assert_eq!(detect_delimiter(data), b';');
    }

    #[test]
    fn strips_leading_bom() {
        assert_eq!(strip_bom("\u{feff}name,url"), "name,url");
        assert_eq!(strip_bom("name,url"), "name,url");
    }

    #[test]
    fn email_detection() {
        assert!(looks_like_email("alice@example.com"));
        assert!(looks_like_email("a.b+c@mail.co.uk"));
        assert!(!looks_like_email("alice@localhost")); // no dot in domain
        assert!(!looks_like_email("not an email"));
        assert!(!looks_like_email("https://example.com"));
        assert!(!looks_like_email(""));
    }

    #[test]
    fn url_detection() {
        assert!(looks_like_url("https://example.com/login"));
        assert!(looks_like_url("http://sub.example.org"));
        assert!(looks_like_url("example.com")); // bare host
        assert!(!looks_like_url("alice@example.com")); // email, not url
        assert!(!looks_like_url("just a note"));
        assert!(!looks_like_url(""));
    }

    #[test]
    fn totp_detection() {
        assert!(looks_like_totp(
            "otpauth://totp/Example:alice?secret=JBSWY3DPEHPK3PXP"
        ));
        assert!(looks_like_totp("JBSWY3DPEHPK3PXP234")); // base32, >=16 chars
        assert!(!looks_like_totp("jbsw y3dp ehpk 3pxp 234")); // spaced/lowercase: not importable as-is
        assert!(!looks_like_totp("short")); // too short
        assert!(!looks_like_totp("has-symbols-!@#$%^&*()")); // not base32
        assert!(!looks_like_totp(""));
    }

    const CHROME_CSV: &str = "name,url,username,password,note\n\
Email,https://mail.example,alice,s3cr3t,primary\n\
Bank,https://bank.example,bob,hunter2,\n";

    #[test]
    fn analyze_chrome_csv() {
        let a = analyze(CHROME_CSV).unwrap();
        assert_eq!(a.columns.len(), 5);
        assert_eq!(a.columns[0].header, "name");
        assert_eq!(a.columns[0].sample_values, vec!["Email", "Bank"]);
        assert_eq!(a.suggested.username, Some(2));
        assert_eq!(a.suggested.password, Some(3));
        assert_eq!(a.confidence.password, Some(Confidence::High));
    }

    #[test]
    fn analyze_detects_semicolons_and_bom() {
        let data = "\u{feff}name;url;password\nSite;https://x.example;pw\n";
        let a = analyze(data).unwrap();
        assert_eq!(a.columns.len(), 3);
        assert_eq!(a.suggested.url, Some(1));
        assert_eq!(a.suggested.password, Some(2));
    }

    #[test]
    fn analyze_empty_input_errors() {
        assert!(matches!(analyze(""), Err(BackupError::EmptyCsv)));
        assert!(matches!(analyze("   \n  "), Err(BackupError::EmptyCsv)));
    }

    #[test]
    fn import_chrome_csv_with_suggested_mapping() {
        let a = analyze(CHROME_CSV).unwrap();
        let (backup, report) = import(CHROME_CSV, &a.suggested).unwrap();
        assert_eq!(
            report,
            ImportReport {
                imported: 2,
                skipped: 0
            }
        );
        let v = &backup.vaults[0];
        assert_eq!(v.name, "CSV Import");
        assert_eq!(v.logins[0].title, "Email");
        assert_eq!(v.logins[0].username.as_deref(), Some("alice"));
        assert_eq!(v.logins[0].password.as_deref(), Some("s3cr3t"));
        assert_eq!(
            v.logins[0].websites,
            vec!["https://mail.example".to_string()]
        );
        assert_eq!(v.logins[0].notes.as_deref(), Some("primary"));
        assert_eq!(v.logins[1].notes, None); // empty cell -> None
    }

    #[test]
    fn import_honors_user_edited_mapping() {
        let mut a = analyze(CHROME_CSV).unwrap();
        a.suggested.notes = None; // user removes the notes mapping
        let (backup, _) = import(CHROME_CSV, &a.suggested).unwrap();
        assert!(backup.vaults[0].logins.iter().all(|l| l.notes.is_none()));
    }

    #[test]
    fn import_skips_empty_rows_and_reports() {
        // 2 valid rows, 1 all-empty row.
        let data = "name,username,password\nA,alice,pw1\n,,\nB,bob,pw2\n";
        let mapping = ColumnMapping {
            title: Some(0),
            username: Some(1),
            password: Some(2),
            ..Default::default()
        };
        let (backup, report) = import(data, &mapping).unwrap();
        assert_eq!(
            report,
            ImportReport {
                imported: 2,
                skipped: 1
            }
        );
        assert_eq!(backup.vaults[0].logins.len(), 2);
    }

    #[test]
    fn import_tolerates_ragged_rows() {
        // Second row has fewer columns than the header; it must not error.
        let data = "name,username,password\nFull,alice,pw\nPartial,bob\n";
        let mapping = ColumnMapping {
            title: Some(0),
            username: Some(1),
            password: Some(2),
            ..Default::default()
        };
        let (backup, report) = import(data, &mapping).unwrap();
        assert_eq!(report.imported, 2);
        let partial = &backup.vaults[0].logins[1];
        assert_eq!(partial.username.as_deref(), Some("bob"));
        assert_eq!(partial.password, None); // missing column -> None
    }

    #[test]
    fn import_title_fallback_chain() {
        let data = "url,username,password\nhttps://only-url.example,,\n,carol,\n,,lonelypw\n";
        let mapping = ColumnMapping {
            url: Some(0),
            username: Some(1),
            password: Some(2),
            ..Default::default()
        };
        let (backup, _) = import(data, &mapping).unwrap();
        let logins = &backup.vaults[0].logins;
        assert_eq!(logins[0].title, "https://only-url.example"); // url fallback
        assert_eq!(logins[1].title, "carol"); // username fallback
        assert_eq!(logins[2].title, "Untitled"); // last-resort
    }

    #[test]
    fn import_empty_input_errors() {
        let mapping = ColumnMapping::default();
        assert!(matches!(import("", &mapping), Err(BackupError::EmptyCsv)));
    }

    fn login(title: &str) -> Login {
        Login {
            title: title.to_owned(),
            ..Default::default()
        }
    }

    fn vault(logins: Vec<Login>) -> Backup {
        Backup {
            vaults: vec![Vault {
                logins,
                ..Default::default()
            }],
        }
    }

    #[test]
    fn export_keygo_writes_header_and_all_fields() {
        let backup = vault(vec![
            Login {
                title: "Email".into(),
                username: Some("alice".into()),
                password: Some("s3cr3t".into()),
                totp_secret: Some("JBSWY3DPEHPK3PXP".into()),
                websites: vec!["https://mail.example".into()],
                notes: Some("primary".into()),
                ..Default::default()
            },
            login("Bare"),
        ]);
        let csv = export(&backup, ExportPreset::KeyGo).unwrap();
        let lines: Vec<&str> = csv.lines().collect();
        assert_eq!(lines[0], "title,url,username,password,notes,totp");
        assert_eq!(
            lines[1],
            "Email,https://mail.example,alice,s3cr3t,primary,JBSWY3DPEHPK3PXP"
        );
        // Only the title is set; every optional becomes an empty cell.
        assert_eq!(lines[2], "Bare,,,,,");
    }

    #[test]
    fn export_browser_uses_browser_headers_and_omits_totp() {
        let backup = vault(vec![Login {
            title: "Email".into(),
            username: Some("alice".into()),
            password: Some("s3cr3t".into()),
            totp_secret: Some("JBSWY3DPEHPK3PXP".into()),
            websites: vec!["https://mail.example".into()],
            notes: Some("primary".into()),
            ..Default::default()
        }]);
        let csv = export(&backup, ExportPreset::Browser).unwrap();
        let lines: Vec<&str> = csv.lines().collect();
        assert_eq!(lines[0], "name,url,username,password,note");
        assert_eq!(lines[1], "Email,https://mail.example,alice,s3cr3t,primary");
        // The browser layout carries no TOTP column.
        assert!(!csv.contains("JBSWY3DPEHPK3PXP"));
    }

    #[test]
    fn export_empty_backup_writes_header_only() {
        let csv = export(&Backup { vaults: vec![] }, ExportPreset::KeyGo).unwrap();
        assert_eq!(
            csv.lines().collect::<Vec<_>>(),
            ["title,url,username,password,notes,totp"]
        );
    }

    #[test]
    fn export_flattens_logins_across_vaults() {
        let backup = Backup {
            vaults: vec![
                Vault {
                    logins: vec![login("A")],
                    ..Default::default()
                },
                Vault {
                    logins: vec![login("B")],
                    ..Default::default()
                },
            ],
        };
        let lines: Vec<String> = export(&backup, ExportPreset::Browser)
            .unwrap()
            .lines()
            .map(str::to_owned)
            .collect();
        assert_eq!(lines.len(), 3); // header + one row per login
        assert_eq!(lines[1], "A,,,,");
        assert_eq!(lines[2], "B,,,,");
    }

    #[test]
    fn export_keygo_round_trips_through_import() {
        let backup = vault(vec![Login {
            title: "Email".into(),
            username: Some("alice".into()),
            password: Some("s3cr3t".into()),
            totp_secret: Some("JBSWY3DPEHPK3PXP".into()),
            websites: vec!["https://mail.example".into()],
            notes: Some("primary".into()),
            ..Default::default()
        }]);
        let csv = export(&backup, ExportPreset::KeyGo).unwrap();
        let analysis = analyze(&csv).unwrap();
        let (restored, report) = import(&csv, &analysis.suggested).unwrap();
        assert_eq!(report.imported, 1);
        let l = &restored.vaults[0].logins[0];
        assert_eq!(l.title, "Email");
        assert_eq!(l.username.as_deref(), Some("alice"));
        assert_eq!(l.password.as_deref(), Some("s3cr3t"));
        assert_eq!(l.websites, vec!["https://mail.example".to_string()]);
        assert_eq!(l.notes.as_deref(), Some("primary"));
        assert_eq!(l.totp_secret.as_deref(), Some("JBSWY3DPEHPK3PXP"));
    }

    #[test]
    fn export_escapes_delimiters_quotes_and_newlines() {
        let backup = vault(vec![Login {
            title: "Comma, Inc.".into(),
            username: Some("a".into()),
            password: Some("p".into()),
            notes: Some("line1\nline2 \"quoted\"".into()),
            ..Default::default()
        }]);
        let csv = export(&backup, ExportPreset::KeyGo).unwrap();
        // The exact escaping is the csv crate's job; assert it re-imports losslessly.
        let analysis = analyze(&csv).unwrap();
        let (restored, _) = import(&csv, &analysis.suggested).unwrap();
        let l = &restored.vaults[0].logins[0];
        assert_eq!(l.title, "Comma, Inc.");
        assert_eq!(l.notes.as_deref(), Some("line1\nline2 \"quoted\""));
    }

    #[test]
    fn export_preset_headers_analyze_as_high_confidence() {
        // Every header an export preset emits must be recognized by `analyze` at
        // High confidence; otherwise an exported file would re-import with a weak
        // or missing column mapping. A "contains"-only match scores Low, so this
        // assertion fails if any preset header drifts off an exact keyword.
        fn confidence_of(c: &FieldConfidence, field: Field) -> Option<&Confidence> {
            match field {
                Field::Title => c.title.as_ref(),
                Field::Url => c.url.as_ref(),
                Field::Username => c.username.as_ref(),
                Field::Password => c.password.as_ref(),
                Field::Notes => c.notes.as_ref(),
                Field::Totp => c.totp.as_ref(),
            }
        }

        for preset in [ExportPreset::KeyGo, ExportPreset::Browser] {
            // Drive the real export path: an empty backup yields just the header row.
            let csv = export(&Backup { vaults: vec![] }, preset).unwrap();
            let analysis = analyze(&csv).unwrap();
            for &(header, field) in preset.columns() {
                assert_eq!(
                    confidence_of(&analysis.confidence, field),
                    Some(&Confidence::High),
                    "{preset:?} header {header:?} should analyze at High confidence",
                );
            }
        }
    }
}
