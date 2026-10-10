use keygo_core::backup::{
    Backup, ColumnMapping as CoreColumnMapping, Confidence, CsvAnalysis as CoreCsvAnalysis,
    CsvColumn, ExportPreset, FieldConfidence, ImportReport,
};

#[uniffi::remote(Enum)]
enum Confidence {
    High,
    Medium,
    Low,
}

#[uniffi::remote(Enum)]
enum ExportPreset {
    KeyGo,
    Browser,
}

#[uniffi::remote(Record)]
struct CsvColumn {
    pub index: u32,
    pub header: String,
    pub sample_values: Vec<String>,
}

#[uniffi::remote(Record)]
struct ImportReport {
    pub imported: u32,
    pub skipped: u32,
}

#[uniffi::remote(Record)]
struct FieldConfidence {
    pub title: Option<Confidence>,
    pub url: Option<Confidence>,
    pub username: Option<Confidence>,
    pub password: Option<Confidence>,
    pub notes: Option<Confidence>,
    pub totp: Option<Confidence>,
}

#[derive(uniffi::Enum)]
pub enum JsonEncryption {
    Passphrase,
    Ark,
}

#[derive(uniffi::Record, Default)]
pub struct ColumnMapping {
    pub title: Option<u32>,
    pub url: Option<u32>,
    pub username: Option<u32>,
    pub password: Option<u32>,
    pub notes: Option<u32>,
    pub totp: Option<u32>,
}

impl From<CoreColumnMapping> for ColumnMapping {
    fn from(m: CoreColumnMapping) -> Self {
        Self {
            title: m.title.map(|i| i as u32),
            url: m.url.map(|i| i as u32),
            username: m.username.map(|i| i as u32),
            password: m.password.map(|i| i as u32),
            notes: m.notes.map(|i| i as u32),
            totp: m.totp.map(|i| i as u32),
        }
    }
}

impl From<ColumnMapping> for CoreColumnMapping {
    fn from(m: ColumnMapping) -> Self {
        Self {
            title: m.title.map(|i| i as usize),
            url: m.url.map(|i| i as usize),
            username: m.username.map(|i| i as usize),
            password: m.password.map(|i| i as usize),
            notes: m.notes.map(|i| i as usize),
            totp: m.totp.map(|i| i as usize),
        }
    }
}

#[derive(uniffi::Record)]
pub struct CsvAnalysis {
    pub columns: Vec<CsvColumn>,
    pub suggested: ColumnMapping,
    pub confidence: FieldConfidence,
}

impl From<CoreCsvAnalysis> for CsvAnalysis {
    fn from(a: CoreCsvAnalysis) -> Self {
        Self {
            columns: a.columns,
            suggested: a.suggested.into(),
            confidence: a.confidence,
        }
    }
}

#[derive(uniffi::Record)]
pub struct CsvImportResult {
    pub backup: Backup,
    pub report: ImportReport,
}
