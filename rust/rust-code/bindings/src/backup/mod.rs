mod csv;
mod model;

use std::sync::Arc;

use keygo_core::backup::{
    Backup, BackupCredential as CoreCredential, BackupError as CoreError, ExportPreset, KeySource,
    csv as core_csv, json as core_json,
};
use keygo_core::crypto::AccountRootKey;

use self::csv::{ColumnMapping, CsvAnalysis, CsvImportResult, JsonEncryption};

#[derive(uniffi::Enum)]
pub enum BackupCredential {
    Passphrase { bytes: Vec<u8> },
    Ark { key: AccountRootKey },
}

impl BackupCredential {
    /// Borrow as the core credential. Core takes the secret by reference, so this
    /// cannot be a `From` impl - the borrow has to outlive the call, not the value.
    fn as_core(&self) -> CoreCredential<'_> {
        match self {
            Self::Passphrase { bytes } => CoreCredential::Passphrase(bytes),
            Self::Ark { key } => CoreCredential::Ark(key),
        }
    }
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum BackupError {
    #[error("crypto error: {0}")]
    Crypto(String),
    #[error("json error: {0}")]
    Json(String),
    #[error("invalid base64 in backup payload or header")]
    Base64,
    #[error("unsupported backup version: {0}")]
    UnsupportedVersion(u32),
    #[error("malformed encryption header")]
    MalformedHeader,
    #[error("credential does not match the backup's key source")]
    CredentialMismatch,
    #[error("malformed csv: {0}")]
    Csv(String),
    #[error("csv contained no rows")]
    EmptyCsv,
}

impl From<CoreError> for BackupError {
    fn from(e: CoreError) -> Self {
        match e {
            CoreError::Crypto(c) => Self::Crypto(format!("{c}")),
            CoreError::Json(j) => Self::Json(format!("{j}")),
            CoreError::Base64 => Self::Base64,
            CoreError::UnsupportedVersion(v) => Self::UnsupportedVersion(v),
            CoreError::MalformedHeader => Self::MalformedHeader,
            CoreError::CredentialMismatch => Self::CredentialMismatch,
            CoreError::Csv(s) => Self::Csv(s),
            CoreError::EmptyCsv => Self::EmptyCsv,
        }
    }
}

#[derive(uniffi::Object)]
pub struct JsonBackupManager;

#[uniffi::export]
impl JsonBackupManager {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn export(
        &self,
        backup: Backup,
        credential: BackupCredential,
    ) -> Result<String, BackupError> {
        Ok(core_json::export(&backup, credential.as_core())?)
    }

    pub fn import(
        &self,
        data: String,
        credential: BackupCredential,
    ) -> Result<Backup, BackupError> {
        Ok(core_json::import(&data, credential.as_core())?)
    }

    pub fn inspect(&self, data: String) -> Result<JsonEncryption, BackupError> {
        Ok(match core_json::inspect(&data)? {
            KeySource::Passphrase => JsonEncryption::Passphrase,
            KeySource::Ark => JsonEncryption::Ark,
        })
    }
}

#[derive(uniffi::Object)]
pub struct CsvBackupManager;

#[uniffi::export]
impl CsvBackupManager {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn analyze(&self, data: String) -> Result<CsvAnalysis, BackupError> {
        Ok(core_csv::analyze(&data)?.into())
    }

    pub fn import(
        &self,
        data: String,
        mapping: ColumnMapping,
    ) -> Result<CsvImportResult, BackupError> {
        let (backup, report) = core_csv::import(&data, &mapping.into())?;
        Ok(CsvImportResult { backup, report })
    }

    pub fn export(&self, backup: Backup, preset: ExportPreset) -> Result<String, BackupError> {
        Ok(core_csv::export(&backup, preset)?)
    }
}
