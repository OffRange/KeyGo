use crate::crypto::error::CryptoError;

#[derive(Debug, thiserror::Error)]
pub enum BackupError {
    #[error("crypto error: {0}")]
    Crypto(#[from] CryptoError),
    #[error("json error: {0}")]
    Json(#[from] serde_json::Error),
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
