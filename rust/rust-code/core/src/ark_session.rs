use crate::crypto::error::CryptoError;
use crate::crypto::primitive::wrap_key::{AeadWrappedKey, KeyWrapper};
use crate::crypto::types::{UserId, VaultId};
use crate::crypto::{AccountRootKey, RootKEK, VaultKey};
use std::sync::Mutex;

#[derive(Debug, thiserror::Error)]
pub enum ArkSessionError {
    #[error("No active session")]
    Locked,
    #[error("{0}")]
    KeyWrap(#[from] CryptoError),
}

type ArkSessionResult<T> = Result<T, ArkSessionError>;

pub struct ArkSession {
    ark: Mutex<Option<AccountRootKey>>,
}

impl Default for ArkSession {
    fn default() -> Self {
        Self::new()
    }
}

impl ArkSession {
    pub fn new() -> Self {
        Self {
            ark: Mutex::new(None),
        }
    }

    pub fn unlock(
        &self,
        kek: RootKEK,
        wrapped_key: AeadWrappedKey<AccountRootKey, RootKEK>,
        aad: UserId,
    ) -> ArkSessionResult<()> {
        let ark = kek.unwrap_key(&wrapped_key, &aad)?;
        *self.lock() = Some(ark);
        Ok(())
    }

    pub fn end(&self) {
        self.lock().take();
    }

    pub fn is_active(&self) -> bool {
        self.lock().is_some()
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, Option<AccountRootKey>> {
        self.ark.lock().unwrap_or_else(|e| e.into_inner())
    }

    pub fn unwrap_vault_key(
        &self,
        wrapped: AeadWrappedKey<VaultKey, AccountRootKey>,
        aad: VaultId,
    ) -> ArkSessionResult<VaultKey> {
        let guard = self.lock();
        let ark = guard.as_ref().ok_or(ArkSessionError::Locked)?;
        Ok(ark.unwrap_key(&wrapped, &aad)?)
    }

    pub fn wrap_vault_key(
        &self,
        vault_key: VaultKey,
        aad: VaultId,
    ) -> ArkSessionResult<AeadWrappedKey<VaultKey, AccountRootKey>> {
        let guard = self.lock();
        let ark = guard.as_ref().ok_or(ArkSessionError::Locked)?;
        Ok(ark.wrap_key(&vault_key, &aad)?)
    }
}
