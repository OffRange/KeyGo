mod csv;
mod model;

use std::sync::Arc;

use keygo_core::ark_session::ArkSessionError;
use keygo_core::backup::{
    Backup, BackupCredential as CoreCredential, BackupError as CoreError, ExportPreset, KeySource,
    csv as core_csv, json as core_json,
};

use self::csv::{ColumnMapping, CsvAnalysis, CsvImportResult, JsonEncryption};
use crate::ark_session::ArkCredential;

#[derive(uniffi::Enum)]
pub enum BackupCredential {
    Passphrase { bytes: Vec<u8> },
    Ark { credential: Arc<ArkCredential> },
}

impl BackupCredential {
    /// Run `f` with the core credential. The ARK is borrowed from the session for exactly the
    /// length of the call, so it is never copied out to build a credential.
    fn with_core<R>(
        &self,
        f: impl FnOnce(CoreCredential<'_>) -> Result<R, CoreError>,
    ) -> Result<R, BackupError> {
        match self {
            Self::Passphrase { bytes } => Ok(f(CoreCredential::Passphrase(bytes))?),
            Self::Ark { credential } => credential
                .session()
                .with_ark(|ark| f(CoreCredential::Ark(ark)))
                .map_err(BackupError::from)?
                .map_err(BackupError::from),
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
    #[error("no active session")]
    Locked,
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

impl From<ArkSessionError> for BackupError {
    fn from(e: ArkSessionError) -> Self {
        match e {
            ArkSessionError::Locked => Self::Locked,
            other => Self::Crypto(format!("{other}")),
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
        credential.with_core(|c| core_json::export(&backup, c))
    }

    pub fn import(
        &self,
        data: String,
        credential: BackupCredential,
    ) -> Result<Backup, BackupError> {
        credential.with_core(|c| core_json::import(&data, c))
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

#[cfg(test)]
mod tests {
    use keygo_core::crypto::KeyMaterial;

    use super::*;
    use crate::ark_session::ArkSession;

    #[test]
    fn an_ark_credential_resolves_to_its_own_sessions_ark() {
        let session = ArkSession::new();
        session
            .create_account("hunter2".to_string())
            .expect("account creation");
        let expected = session.export_ark().expect("live ark");

        let credential = BackupCredential::Ark {
            credential: Arc::clone(&session).ark_credential(),
        };

        let seen = credential
            .with_core(|core| match core {
                CoreCredential::Ark(ark) => Ok(ark.as_bytes().to_vec()),
                CoreCredential::Passphrase(_) => panic!("expected the ark key source"),
            })
            .expect("credential resolves");

        assert_eq!(expected, seen);
    }

    #[test]
    fn an_ark_credential_from_a_locked_session_reports_locked() {
        let session = ArkSession::new();

        let credential = BackupCredential::Ark {
            credential: session.ark_credential(),
        };

        let resolved = credential.with_core(|_| Ok(()));

        assert!(matches!(resolved, Err(BackupError::Locked)));
    }
}
