//! CSV import and export.
//!
//! [`analyze`] inspects an unknown CSV and proposes a [`ColumnMapping`]; the user
//! edits it; [`import`] then reads the file under that mapping. [`export`] writes
//! one back out in a chosen [`ExportPreset`]. The column-guessing heuristics that
//! back `analyze` live in `detect`.

mod detect;

use csv::StringRecord;

use self::detect::{build_mapping, build_reader, strip_bom};
use crate::backup::{Backup, BackupError, Login, Vault};

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

/// How many data rows feed the analysis. Must be `>= DISPLAY_SAMPLES`.
const SAMPLE_ROWS: usize = 10;
const DISPLAY_SAMPLES: usize = 5;

/// Inspect a CSV and return a review-ready analysis: the columns (with a few
/// sample values each), a suggested editable mapping, and per-field confidence.
///
/// Column types are inferred from the header names and from the first
/// `SAMPLE_ROWS` parseable data rows (malformed rows are skipped). Only those
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
