use crate::item::EncryptedItemBlob;
use crate::key_wrap::{KeyWrapError, WrappedKeyBlob};
use keygo_core::ark_session::{
    AccountRecord as CoreAccountRecord, ArkSession as CoreArkSession,
    ArkSessionError as CoreArkSessionError, NewAccount as CoreNewAccount, PasswordWrappedArk,
};
use keygo_core::crypto::{CryptoError, NamespaceId, SecretKind, UserId, VaultId, VaultKey};
use std::sync::Arc;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ArkSessionError {
    #[error("No active session")]
    Locked,
    #[error("Wrong password")]
    WrongPassword,
    #[error("Key derivation failed: {0}")]
    Derivation(String),
    #[error("Decryption failed: ciphertext invalid or tampered")]
    DecryptionFailed,
    #[error("{0}")]
    KeyWrap(#[from] KeyWrapError),
}

impl From<CoreArkSessionError> for ArkSessionError {
    fn from(value: CoreArkSessionError) -> Self {
        match value {
            CoreArkSessionError::Locked => Self::Locked,
            CoreArkSessionError::WrongPassword => Self::WrongPassword,
            CoreArkSessionError::Derivation(e) => Self::Derivation(e.to_string()),
            CoreArkSessionError::KeyWrap(CryptoError::DecryptionFailed) => Self::DecryptionFailed,
            CoreArkSessionError::KeyWrap(e) => e.into(),
        }
    }
}

impl From<CryptoError> for ArkSessionError {
    fn from(value: CryptoError) -> Self {
        Self::KeyWrap(value.into())
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

impl From<CoreNewAccount> for NewAccount {
    fn from(account: CoreNewAccount) -> Self {
        Self {
            user_id: account.user_id,
            salt: account.password_wrapped_ark.salt,
            password_wrapped_ark: account.password_wrapped_ark.wrapped.into(),
            vault_id: account.vault_id,
            wrapped_vault_key: account.wrapped_vault_key.into(),
        }
    }
}

#[derive(uniffi::Record)]
pub struct PasswordWrapped {
    pub salt: Vec<u8>,
    pub wrapped: WrappedKeyBlob,
}

impl From<PasswordWrappedArk> for PasswordWrapped {
    fn from(value: PasswordWrappedArk) -> Self {
        Self {
            salt: value.salt,
            wrapped: value.wrapped.into(),
        }
    }
}

#[derive(uniffi::Record)]
pub struct AccountRecord {
    pub wrapped_key: WrappedKeyBlob,
    pub data: EncryptedItemBlob,
}

impl From<CoreAccountRecord> for AccountRecord {
    fn from(record: CoreAccountRecord) -> Self {
        Self {
            wrapped_key: record.wrapped_key.into(),
            data: record.data.into(),
        }
    }
}

impl AccountRecord {
    fn into_core(self) -> Result<CoreAccountRecord, CryptoError> {
        Ok(CoreAccountRecord {
            wrapped_key: self.wrapped_key.into_sealed()?,
            data: self.data.into_sealed()?,
        })
    }
}

fn password_wrapped_ark(
    salt: Vec<u8>,
    wrapped: WrappedKeyBlob,
) -> Result<PasswordWrappedArk, CryptoError> {
    Ok(PasswordWrappedArk {
        salt,
        wrapped: wrapped.into_sealed()?,
    })
}

#[derive(uniffi::Object)]
pub struct ArkCredential {
    ark_session: Arc<ArkSession>,
}

impl ArkCredential {
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
        Ok(self.session.create_account(&password)?.into())
    }

    pub fn unlock_with_password(
        &self,
        password: String,
        salt: Vec<u8>,
        wrapped: WrappedKeyBlob,
        user_id: UserId,
    ) -> Result<(), ArkSessionError> {
        let wrapped = password_wrapped_ark(salt, wrapped)?;
        Ok(self
            .session
            .unlock_with_password(&password, &wrapped, user_id)?)
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
        let wrapped = password_wrapped_ark(salt, wrapped)?;
        Ok(self.session.verify_password(&password, &wrapped, user_id)?)
    }

    pub fn verify_ark(&self, ark: Vec<u8>) -> Result<bool, ArkSessionError> {
        Ok(self.session.verify_ark(&ark)?)
    }

    pub fn rewrap_for_new_password(
        &self,
        new_password: String,
        user_id: UserId,
    ) -> Result<PasswordWrapped, ArkSessionError> {
        Ok(self
            .session
            .rewrap_for_new_password(&new_password, user_id)?
            .into())
    }

    pub fn unwrap_vault_key(
        &self,
        wrapped: WrappedKeyBlob,
        vault_id: VaultId,
    ) -> Result<VaultKey, ArkSessionError> {
        Ok(self
            .session
            .unwrap_vault_key(&wrapped.into_sealed()?, vault_id)?)
    }

    pub fn wrap_vault_key(
        &self,
        vault_key: VaultKey,
        vault_id: VaultId,
    ) -> Result<WrappedKeyBlob, ArkSessionError> {
        Ok(self.session.wrap_vault_key(&vault_key, vault_id)?.into())
    }

    pub fn seal_account_record(
        &self,
        namespace: NamespaceId,
        kind: SecretKind,
        plaintext: Vec<u8>,
    ) -> Result<AccountRecord, ArkSessionError> {
        Ok(self
            .session
            .seal_record(namespace, kind, &plaintext)?
            .into())
    }

    pub fn open_account_record(
        &self,
        namespace: NamespaceId,
        kind: SecretKind,
        record: AccountRecord,
    ) -> Result<Vec<u8>, ArkSessionError> {
        Ok(self
            .session
            .open_record(namespace, kind, &record.into_core()?)?)
    }

    pub fn ark_credential(self: Arc<Self>) -> Arc<ArkCredential> {
        Arc::new(ArkCredential { ark_session: self })
    }
}
