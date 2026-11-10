use super::error::{SyncError, SyncResult};
use keygo_dpop::{DpopSigner, Signature, VerifyingKey};
use std::sync::Arc;

#[derive(Debug, thiserror::Error)]
#[error("{message}")]
pub struct SignError {
    pub message: String,
}

/// A hardware-backed P-256 key held by the host.
pub trait DpopKey: Send + Sync {
    /// The 65-byte uncompressed P-256 point.
    fn public_key(&self) -> Result<Vec<u8>, SignError>;
    /// SHA256withECDSA over `message`, DER encoded.
    /// Can be called from several threads at once.
    fn sign(&self, message: Vec<u8>) -> Result<Vec<u8>, SignError>;
}

pub struct ForeignSigner {
    key: Arc<dyn DpopKey>,
    verifying: VerifyingKey,
}

impl ForeignSigner {
    pub fn new(key: Arc<dyn DpopKey>) -> SyncResult<Self> {
        let public_key = key.public_key().map_err(|e| SyncError::Client {
            message: format!("could not read the DPoP public key: {e}"),
        })?;
        let verifying =
            VerifyingKey::from_sec1_bytes(&public_key).map_err(|_| SyncError::Client {
                message: "the DPoP public key is not a P-256 point".into(),
            })?;
        Ok(Self { key, verifying })
    }
}

impl DpopSigner for ForeignSigner {
    type Error = SignError;

    fn verifying_key(&self) -> &VerifyingKey {
        &self.verifying
    }

    fn sign(&self, msg: &[u8]) -> Result<Signature, SignError> {
        let der = self.key.sign(msg.to_vec())?;
        Signature::from_der(&der).map_err(|_| SignError {
            message: "the DPoP signature is not DER".into(),
        })
    }
}
