pub mod encryption;
pub mod error;
pub mod format;
pub mod key;
pub mod model;

pub use encryption::BackupCredential;
pub use error::BackupError;
pub use format::csv::{
    ColumnMapping, Confidence, CsvAnalysis, CsvColumn, ExportPreset, FieldConfidence, ImportReport,
};
pub use format::{csv, json};
pub use key::BackupKey;
pub use model::{Backup, Card, Login, Passkey, Vault};

/// Version stamped into newly written backups.
pub const CURRENT_VERSION: u32 = 1;

/// Oldest envelope version this build can still read. Backups are long-lived: a
/// file written by an old build may be restored by a much newer one. When
/// `CURRENT_VERSION` is bumped, keep older versions readable here (and preserve
/// their exact AAD/BCS layout - see [`encryption::BackupAad`]) instead of
/// rejecting them. Per-version decode branches belong in the format's `import`,
/// keyed off the envelope version.
pub const MIN_SUPPORTED_VERSION: u32 = 1;
