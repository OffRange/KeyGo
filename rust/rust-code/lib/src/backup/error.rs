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
    #[error("encryption header and payload disagree")]
    EncryptionMismatch,
    #[error("a credential is required to read this encrypted backup")]
    MissingCredential,
    #[error("a credential was supplied for a plaintext backup")]
    UnexpectedCredential,
    #[error("credential does not match the backup's key source")]
    CredentialMismatch,
}
