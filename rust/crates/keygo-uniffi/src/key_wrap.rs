use keygo_core::crypto::{CryptoError, ItemAad, ItemKey, Sealed, VaultKey, Wraps};
use std::sync::Arc;

#[derive(uniffi::Record)]
pub struct WrappedKeyBlob {
    pub ciphertext: Vec<u8>,
    pub nonce: Vec<u8>,
}

impl<T> From<Sealed<T>> for WrappedKeyBlob {
    fn from(sealed: Sealed<T>) -> Self {
        let (nonce, ciphertext) = sealed.into_parts();
        Self { ciphertext, nonce }
    }
}

impl WrappedKeyBlob {
    pub(crate) fn into_sealed<T>(self) -> Result<Sealed<T>, CryptoError> {
        Sealed::from_parts(&self.nonce, self.ciphertext)
    }
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum KeyWrapError {
    #[error("Key wrap failed")]
    WrapFailed,
    #[error("Key unwrap failed: wrong key or corrupted data")]
    UnwrapFailed,
    #[error("Invalid key length: expected {expected} bytes, got {got} bytes")]
    InvalidKeyLength { expected: u64, got: u64 },
    #[error("{0}")]
    Other(String),
}

impl From<CryptoError> for KeyWrapError {
    fn from(value: CryptoError) -> Self {
        match value {
            CryptoError::KeyWrapFailed => Self::WrapFailed,
            CryptoError::KeyUnwrapFailed => Self::UnwrapFailed,
            CryptoError::InvalidKeyLength { expected, got } => Self::InvalidKeyLength {
                expected: expected as u64,
                got: got as u64,
            },
            other => Self::Other(other.to_string()),
        }
    }
}

#[derive(uniffi::Object)]
pub struct KeyWrapper;

#[uniffi::export]
impl KeyWrapper {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn wrap_item_key(
        &self,
        vault_key: VaultKey,
        item_key: ItemKey,
        aad: ItemAad,
    ) -> Result<WrappedKeyBlob, KeyWrapError> {
        Ok(vault_key.wrap_key(&item_key, &aad)?.into())
    }

    pub fn unwrap_item_key(
        &self,
        vault_key: VaultKey,
        wrapped: WrappedKeyBlob,
        aad: ItemAad,
    ) -> Result<ItemKey, KeyWrapError> {
        Ok(vault_key.unwrap_key(&wrapped.into_sealed()?, &aad)?)
    }
}
