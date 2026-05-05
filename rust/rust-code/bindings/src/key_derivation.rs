use lib::crypto::TryDeriveFrom;
use lib::crypto::error::CryptoError;
use lib::crypto::keys::RootKEK;
use lib::crypto::primitive::argon2::MIN_SALT_LEN;
use lib::crypto::random::random_bytes;
use std::sync::Arc;

const PASSWORD_DOMAIN: &[u8] = b"v1:kek/pwd";
const RECOVERY_KEY_DOMAIN: &[u8] = b"v1:kek/rk";
const SALT_LEN: usize = MIN_SALT_LEN;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum KeyDerivationError {
    #[error("Key derivation failed: {0}")]
    Failed(String),
    #[error("{0}")]
    Other(String),
}

impl From<CryptoError> for KeyDerivationError {
    fn from(value: CryptoError) -> Self {
        match value {
            CryptoError::KdfError(msg) => Self::Failed(msg),
            CryptoError::InvalidKeyLength { expected, got } => Self::Failed(format!(
                "invalid key length: expected {expected}, got {got}"
            )),
            other => Self::Other(format!("{other}")),
        }
    }
}

#[derive(uniffi::Object)]
pub struct KeyDeriver;

#[uniffi::export]
impl KeyDeriver {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    /// Generate a fresh random salt suitable for password-based KEK derivation.
    /// Persist this salt alongside the credential so the same KEK can be re-derived on login.
    pub fn generate_salt(&self) -> Vec<u8> {
        random_bytes::<SALT_LEN>().to_vec()
    }

    pub fn derive_root_kek_from_password(
        &self,
        password: String,
        salt: Vec<u8>,
    ) -> Result<RootKEK, KeyDerivationError> {
        Ok(RootKEK::try_derive_from(
            password.as_bytes(),
            &salt,
            PASSWORD_DOMAIN,
        )?)
    }

    pub fn derive_root_kek_from_recovery_key(
        &self,
        recovery_key: Vec<u8>,
        salt: Vec<u8>,
    ) -> Result<RootKEK, KeyDerivationError> {
        Ok(RootKEK::try_derive_from(
            &recovery_key,
            &salt,
            RECOVERY_KEY_DOMAIN,
        )?)
    }
}
