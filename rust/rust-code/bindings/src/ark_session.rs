use crate::key_wrap::{KeyWrapError, WrappedKeyBlob};
use keygo_core::ark_session::{
    ArkSession as CoreArkSession, ArkSessionError as CoreArkSessionError,
};
use keygo_core::crypto::primitive::wrap_key::{AeadWrappedKey, WrappedKey};
use keygo_core::crypto::types::{UserId, VaultId};
use keygo_core::crypto::{RootKEK, VaultKey};
use std::sync::Arc;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ArkSessionError {
    #[error("No active session")]
    Locked,
    #[error("{0}")]
    KeyWrap(#[from] KeyWrapError),
}

impl From<CoreArkSessionError> for ArkSessionError {
    fn from(value: CoreArkSessionError) -> Self {
        match value {
            CoreArkSessionError::Locked => Self::Locked,
            CoreArkSessionError::KeyWrap(crypto_error) => Self::KeyWrap(crypto_error.into()),
        }
    }
}

#[derive(uniffi::Object)]
struct ArkSession {
    session: CoreArkSession,
}

#[uniffi::export]
impl ArkSession {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            session: CoreArkSession::new(),
        })
    }

    pub fn unlock(
        &self,
        kek: RootKEK,
        wrapped: WrappedKeyBlob,
        user_id: UserId,
    ) -> Result<(), ArkSessionError> {
        let wrapped = AeadWrappedKey::from_parts_bytes(wrapped.ciphertext, &wrapped.nonce);
        self.session
            .unlock(kek, wrapped, user_id)
            .map_err(ArkSessionError::from)
    }

    pub fn end(&self) {
        self.session.end()
    }

    pub fn is_active(&self) -> bool {
        self.session.is_active()
    }

    pub fn unwrap_vault_key(
        &self,
        wrapped: WrappedKeyBlob,
        vault_id: VaultId,
    ) -> Result<VaultKey, ArkSessionError> {
        let wrapped = AeadWrappedKey::from_parts_bytes(wrapped.ciphertext, &wrapped.nonce);
        self.session
            .unwrap_vault_key(wrapped, vault_id)
            .map_err(ArkSessionError::from)
    }

    pub fn wrap_vault_key(
        &self,
        vault_key: VaultKey,
        vault_id: VaultId,
    ) -> Result<WrappedKeyBlob, ArkSessionError> {
        self.session
            .wrap_vault_key(vault_key, vault_id)
            .map_err(ArkSessionError::from)
            .map(|wrapped| WrappedKeyBlob {
                ciphertext: wrapped.ciphertext().to_vec(),
                nonce: wrapped.nonce_bytes().to_vec(),
            })
    }
}
