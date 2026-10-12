use keygo_core::backup::{
    Backup, ColumnMapping, Confidence, CsvAnalysis, CsvColumn, ExportPreset, FieldConfidence,
    ImportReport,
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

#[uniffi::remote(Record)]
struct ColumnMapping {
    pub title: Option<u32>,
    pub url: Option<u32>,
    pub username: Option<u32>,
    pub password: Option<u32>,
    pub notes: Option<u32>,
    pub totp: Option<u32>,
}

#[uniffi::remote(Record)]
struct CsvAnalysis {
    pub columns: Vec<CsvColumn>,
    pub suggested: ColumnMapping,
    pub confidence: FieldConfidence,
}

#[derive(uniffi::Record)]
pub struct CsvImportResult {
    pub backup: Backup,
    pub report: ImportReport,
}
