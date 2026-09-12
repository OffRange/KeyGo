use crate::key_wrap::{KeyWrapError, WrappedKeyBlob};
use keygo_core::ark_session::{
    ArkSession as CoreArkSession, ArkSessionError as CoreArkSessionError,
};
use keygo_core::crypto::VaultKey;
use keygo_core::crypto::primitive::wrap_key::{AeadWrappedKey, WrappedKey};
use keygo_core::crypto::types::{UserId, VaultId};
use std::sync::Arc;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ArkSessionError {
    #[error("No active session")]
    Locked,
    #[error("Wrong password")]
    WrongPassword,
    #[error("Key derivation failed: {0}")]
    Derivation(String),
    #[error("{0}")]
    KeyWrap(#[from] KeyWrapError),
}

impl From<CoreArkSessionError> for ArkSessionError {
    fn from(value: CoreArkSessionError) -> Self {
        match value {
            CoreArkSessionError::Locked => Self::Locked,
            CoreArkSessionError::WrongPassword => Self::WrongPassword,
            CoreArkSessionError::Derivation(msg) => Self::Derivation(msg),
            CoreArkSessionError::KeyWrap(crypto_error) => Self::KeyWrap(crypto_error.into()),
        }
    }
}

#[derive(uniffi::Record)]
pub struct NewAccount {
    pub user_id: UserId,
    pub salt: Vec<u8>,
    pub password_wrapped_ark: WrappedKeyBlob,
    pub vault_id: VaultId,
    pub wrapped_vault_key: WrappedKeyBlob,
}

#[derive(uniffi::Record)]
pub struct PasswordWrapped {
    pub salt: Vec<u8>,
    pub wrapped: WrappedKeyBlob,
}

fn blob<T, W>(wrapped: &impl WrappedKey<T, W>) -> WrappedKeyBlob
where
    T: keygo_core::crypto::KeyMaterial,
    W: keygo_core::crypto::AeadKey,
{
    WrappedKeyBlob {
        ciphertext: wrapped.ciphertext().to_vec(),
        nonce: wrapped.nonce_bytes().to_vec(),
    }
}

#[derive(uniffi::Object)]
pub struct ArkCredential {
    ark_session: Arc<ArkSession>,
}

impl ArkCredential {
    /// The session this credential borrows its key from.
    pub(crate) fn session(&self) -> &CoreArkSession {
        &self.ark_session.session
    }
}

#[derive(uniffi::Object)]
pub struct ArkSession {
    pub(crate) session: CoreArkSession,
}

#[uniffi::export]
impl ArkSession {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            session: CoreArkSession::new(),
        })
    }

    pub fn end(&self) {
        self.session.end()
    }

    pub fn is_active(&self) -> bool {
        self.session.is_active()
    }

    pub fn create_account(&self, password: String) -> Result<NewAccount, ArkSessionError> {
        let account = self.session.create_account(&password)?;
        Ok(NewAccount {
            user_id: account.user_id,
            salt: account.salt,
            password_wrapped_ark: blob(&account.password_wrapped_ark),
            vault_id: account.vault_id,
            wrapped_vault_key: blob(&account.wrapped_vault_key),
        })
    }

    pub fn unlock_with_password(
        &self,
        password: String,
        salt: Vec<u8>,
        wrapped: WrappedKeyBlob,
        user_id: UserId,
    ) -> Result<(), ArkSessionError> {
        let wrapped = AeadWrappedKey::from_parts_bytes(wrapped.ciphertext, &wrapped.nonce);
        Ok(self
            .session
            .unlock_with_password(&password, &salt, wrapped, user_id)?)
    }

    pub fn unlock_with_ark(&self, ark: Vec<u8>) -> Result<(), ArkSessionError> {
        Ok(self.session.unlock_with_ark(&ark)?)
    }

    pub fn export_ark(&self) -> Result<Vec<u8>, ArkSessionError> {
        Ok(self.session.export_ark()?.to_vec())
    }

    pub fn verify_password(
        &self,
        password: String,
        salt: Vec<u8>,
        wrapped: WrappedKeyBlob,
        user_id: UserId,
    ) -> Result<(), ArkSessionError> {
        let wrapped = AeadWrappedKey::from_parts_bytes(wrapped.ciphertext, &wrapped.nonce);
        Ok(self
            .session
            .verify_password(&password, &salt, wrapped, user_id)?)
    }

    pub fn verify_ark(&self, ark: Vec<u8>) -> Result<bool, ArkSessionError> {
        Ok(self.session.verify_ark(&ark)?)
    }

    pub fn rewrap_for_new_password(
        &self,
        new_password: String,
        user_id: UserId,
    ) -> Result<PasswordWrapped, ArkSessionError> {
        let wrapped = self
            .session
            .rewrap_for_new_password(&new_password, user_id)?;
        Ok(PasswordWrapped {
            salt: wrapped.salt,
            wrapped: blob(&wrapped.wrapped),
        })
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
            .map(|wrapped| blob(&wrapped))
    }

    pub fn ark_credential(self: Arc<Self>) -> Arc<ArkCredential> {
        Arc::new(ArkCredential { ark_session: self })
    }
}
