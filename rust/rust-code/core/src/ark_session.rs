use crate::crypto::error::CryptoError;
use crate::crypto::primitive::argon2::MIN_SALT_LEN;
use crate::crypto::primitive::wrap_key::{AeadWrappedKey, KeyWrapper};
use crate::crypto::random::random_bytes;
use crate::crypto::types::{UserId, VaultId};
use crate::crypto::{AccountRootKey, KeyMaterial, RootKEK, TryDeriveFrom, VaultKey};
use std::sync::Mutex;
use subtle::ConstantTimeEq;

#[derive(Debug, thiserror::Error)]
pub enum ArkSessionError {
    #[error("No active session")]
    Locked,
    #[error("Wrong password")]
    WrongPassword,
    #[error("Key derivation failed: {0}")]
    Derivation(String),
    #[error("{0}")]
    KeyWrap(#[from] CryptoError),
}

/// The wrapped output of a freshly generated account. The ARK and the default vault key stay in
/// the session; only these blobs are for the caller to persist.
pub struct NewAccount {
    pub user_id: UserId,
    pub salt: Vec<u8>,
    pub password_wrapped_ark: AeadWrappedKey<AccountRootKey, RootKEK>,
    pub vault_id: VaultId,
    pub wrapped_vault_key: AeadWrappedKey<VaultKey, AccountRootKey>,
}

/// An ARK wrapped under a password-derived KEK, with the salt that KEK was derived over.
pub struct PasswordWrapped {
    pub salt: Vec<u8>,
    pub wrapped: AeadWrappedKey<AccountRootKey, RootKEK>,
}

type ArkSessionResult<T> = Result<T, ArkSessionError>;

const PASSWORD_DOMAIN: &[u8] = b"v1:kek/pwd";
const SALT_LEN: usize = MIN_SALT_LEN;

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

    /// Generate an account and its default vault, wrap both, and leave the session unlocked.
    /// The caller receives blobs to persist and no key material.
    pub fn create_account(&self, password: &str) -> ArkSessionResult<NewAccount> {
        let user_id = UserId::new_v4();
        let vault_id = VaultId::new_v4();
        let ark = AccountRootKey::generate_random();
        let vault_key = VaultKey::generate_random();

        let salt = random_bytes::<SALT_LEN>().to_vec();
        let kek = derive_kek(password, &salt)?;

        let password_wrapped_ark = kek.wrap_key(&ark, &user_id)?;
        let wrapped_vault_key = ark.wrap_key(&vault_key, &vault_id)?;

        *self.lock() = Some(ark);

        Ok(NewAccount {
            user_id,
            salt,
            password_wrapped_ark,
            vault_id,
            wrapped_vault_key,
        })
    }

    pub fn unlock_with_password(
        &self,
        password: &str,
        salt: &[u8],
        wrapped: AeadWrappedKey<AccountRootKey, RootKEK>,
        user_id: UserId,
    ) -> ArkSessionResult<()> {
        let kek = derive_kek(password, salt)?;
        self.unlock(kek, wrapped, user_id)
    }

    /// Take custody of an ARK recovered outside Rust. The only inbound ARK door: the biometric
    /// unlock and the backup escrow both hold their copy under an Android Keystore key, which
    /// only exists on the JVM side.
    pub fn unlock_with_ark(&self, ark: &[u8]) -> ArkSessionResult<()> {
        let ark = AccountRootKey::try_from_bytes(ark)?;
        *self.lock() = Some(ark);
        Ok(())
    }

    /// Hand the ARK out for sealing under an Android Keystore key. The only outbound ARK door.
    /// The session keeps its own copy, so the caller owns the returned bytes and must wipe them.
    pub fn export_ark(&self) -> ArkSessionResult<Vec<u8>> {
        self.with_ark(|ark| ark.as_bytes().to_vec())
    }

    /// Prove a password by unwrapping the stored blob and discarding the result. The session's
    /// own ARK is untouched either way.
    pub fn verify_password(
        &self,
        password: &str,
        salt: &[u8],
        wrapped: AeadWrappedKey<AccountRootKey, RootKEK>,
        user_id: UserId,
    ) -> ArkSessionResult<()> {
        let kek = derive_kek(password, salt)?;
        kek.unwrap_key(&wrapped, &user_id)
            .map_err(|_| ArkSessionError::WrongPassword)?;
        Ok(())
    }

    /// Constant-time compare against the live ARK. Used to prove a biometric reauthentication,
    /// where the Keystore hands back an ARK that has to be checked rather than trusted.
    pub fn verify_ark(&self, candidate: &[u8]) -> bool {
        let guard = self.lock();
        let Some(ark) = guard.as_ref() else {
            return false;
        };
        ark.as_bytes().ct_eq(candidate).into()
    }

    /// Rewrap the live ARK under a KEK derived from a new password over a fresh salt.
    pub fn rewrap_for_new_password(
        &self,
        new_password: &str,
        user_id: UserId,
    ) -> ArkSessionResult<PasswordWrapped> {
        let guard = self.lock();
        let ark = guard.as_ref().ok_or(ArkSessionError::Locked)?;

        let salt = random_bytes::<SALT_LEN>().to_vec();
        let kek = derive_kek(new_password, &salt)?;
        let wrapped = kek.wrap_key(ark, &user_id)?;

        Ok(PasswordWrapped { salt, wrapped })
    }

    /// Borrow the live ARK for the length of `f`. Lets callers inside Rust use the ARK without
    /// it ever being copied out.
    pub fn with_ark<R>(&self, f: impl FnOnce(&AccountRootKey) -> R) -> ArkSessionResult<R> {
        let guard = self.lock();
        let ark = guard.as_ref().ok_or(ArkSessionError::Locked)?;
        Ok(f(ark))
    }
}

fn derive_kek(password: &str, salt: &[u8]) -> ArkSessionResult<RootKEK> {
    RootKEK::try_derive_from(password.as_bytes(), salt, PASSWORD_DOMAIN)
        .map_err(|e| ArkSessionError::Derivation(format!("{e}")))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::crypto::KeyMaterial;

    const PASSWORD: &str = "hunter2";

    fn unlocked() -> (ArkSession, NewAccount) {
        let session = ArkSession::new();
        let new_account = session.create_account(PASSWORD).unwrap();
        (session, new_account)
    }

    #[test]
    fn create_account_leaves_session_unlocked() {
        let (session, _) = unlocked();
        assert!(session.is_active());
    }

    #[test]
    fn create_account_produces_a_blob_the_password_can_unlock() {
        let (session, account) = unlocked();
        session.end();

        session
            .unlock_with_password(
                PASSWORD,
                &account.salt,
                account.password_wrapped_ark,
                account.user_id,
            )
            .unwrap();

        assert!(session.is_active());
    }

    #[test]
    fn create_account_default_vault_key_unwraps_under_the_ark() {
        let (session, account) = unlocked();

        assert!(
            session
                .unwrap_vault_key(account.wrapped_vault_key, account.vault_id)
                .is_ok()
        );
    }

    #[test]
    fn unlock_with_wrong_password_leaves_session_locked() {
        let (session, account) = unlocked();
        session.end();

        let result = session.unlock_with_password(
            "wrong",
            &account.salt,
            account.password_wrapped_ark,
            account.user_id,
        );

        assert!(matches!(result, Err(ArkSessionError::KeyWrap(_))));
        assert!(!session.is_active());
    }

    #[test]
    fn export_and_unlock_with_ark_round_trip() {
        let (session, account) = unlocked();
        let exported = session.export_ark().unwrap();

        let second = ArkSession::new();
        second.unlock_with_ark(&exported).unwrap();

        // Both sessions hold the same ARK, so a key wrapped by one unwraps under the other.
        let wrapped = session
            .wrap_vault_key(VaultKey::generate_random(), account.vault_id)
            .unwrap();
        assert!(second.unwrap_vault_key(wrapped, account.vault_id).is_ok());
    }

    #[test]
    fn export_ark_fails_once_the_session_ends() {
        let (session, _) = unlocked();
        session.end();

        assert!(matches!(session.export_ark(), Err(ArkSessionError::Locked)));
    }

    #[test]
    fn unlock_with_ark_rejects_a_wrong_length_key() {
        let session = ArkSession::new();

        assert!(session.unlock_with_ark(&[0u8; 8]).is_err());
        assert!(!session.is_active());
    }

    #[test]
    fn verify_password_accepts_the_current_password_and_rejects_others() {
        let (session, account) = unlocked();
        let wrapped = session
            .rewrap_for_new_password(PASSWORD, account.user_id)
            .unwrap();

        assert!(
            session
                .verify_password(PASSWORD, &wrapped.salt, wrapped.wrapped, account.user_id)
                .is_ok()
        );

        let wrapped = session
            .rewrap_for_new_password(PASSWORD, account.user_id)
            .unwrap();
        assert!(matches!(
            session.verify_password("nope", &wrapped.salt, wrapped.wrapped, account.user_id),
            Err(ArkSessionError::WrongPassword)
        ));
    }

    #[test]
    fn verify_ark_matches_only_the_live_ark() {
        let (session, _) = unlocked();
        let exported = session.export_ark().unwrap();

        assert!(session.verify_ark(&exported));
        assert!(!session.verify_ark(&[0u8; 32]));

        session.end();
        assert!(!session.verify_ark(&exported));
    }

    #[test]
    fn rewrap_for_new_password_produces_a_blob_the_new_password_unlocks() {
        let (session, account) = unlocked();

        let rewrapped = session
            .rewrap_for_new_password("new-password", account.user_id)
            .unwrap();
        let ark_before = session.export_ark().unwrap();
        session.end();

        session
            .unlock_with_password(
                "new-password",
                &rewrapped.salt,
                rewrapped.wrapped,
                account.user_id,
            )
            .unwrap();

        // Same ARK, only the wrapping changed.
        assert_eq!(ark_before, session.export_ark().unwrap());
    }

    #[test]
    fn rewrap_uses_a_fresh_salt_each_time() {
        let (session, account) = unlocked();

        let first = session
            .rewrap_for_new_password("new-password", account.user_id)
            .unwrap();
        let second = session
            .rewrap_for_new_password("new-password", account.user_id)
            .unwrap();

        assert_ne!(first.salt, second.salt);
    }

    #[test]
    fn rewrap_fails_when_locked() {
        let session = ArkSession::new();

        assert!(matches!(
            session.rewrap_for_new_password("new-password", UserId::new_v4()),
            Err(ArkSessionError::Locked)
        ));
    }

    #[test]
    fn with_ark_runs_the_closure_only_when_unlocked() {
        let (session, _) = unlocked();
        assert_eq!(session.with_ark(|ark| ark.as_bytes().len()).unwrap(), 32);

        session.end();
        assert!(matches!(
            session.with_ark(|_| ()),
            Err(ArkSessionError::Locked)
        ));
    }
}
