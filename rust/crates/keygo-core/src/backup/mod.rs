mod encryption;
mod error;
mod format;
mod key;
mod model;

pub use encryption::{BackupCredential, KeySource};
pub use error::BackupError;
pub use format::csv::{
    ColumnMapping, Confidence, CsvAnalysis, CsvColumn, ExportPreset, FieldConfidence, ImportReport,
};
pub use format::{csv, json};
pub use key::BackupKey;
pub use model::{Backup, Card, Login, Passkey, Vault};

pub const CURRENT_VERSION: u32 = 1;

/// Oldest readable version. On a bump, keep older versions and their AAD layout readable.
pub const MIN_SUPPORTED_VERSION: u32 = 1;
