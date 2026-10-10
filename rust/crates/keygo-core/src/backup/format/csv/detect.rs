use csv::StringRecord;
use email_address::Options;

use super::{ALL_FIELDS, ColumnMapping, Confidence, Field, FieldConfidence};
use crate::totp::is_valid_totp_secret;
use crate::url::sanitize_to_https_url;

const DELIMITERS: [u8; 4] = *b",;\t|";

/// Strip a leading UTF-8 BOM, if present.
pub(super) fn strip_bom(data: &str) -> &str {
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

pub(super) fn build_reader(data: &str) -> csv::Reader<&[u8]> {
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
pub(super) fn build_mapping(
    headers: &[String],
    samples: &[StringRecord],
) -> (ColumnMapping, FieldConfidence) {
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

#[cfg(test)]
mod tests {
    use super::*;

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
}
