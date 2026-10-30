use crate::crypto::kdf::new_salt;
use crate::crypto::{
    AccountRootKey, CryptoError, Encrypts, NamespaceDataAad, NamespaceId, NamespaceKey,
    NamespaceKeyAad, RootKEK, Sealed, SecretKind, SymmetricKey, UserId, VaultId, VaultKey, Wraps,
};
use std::sync::{Mutex, MutexGuard};
use subtle::ConstantTimeEq;
use zeroize::Zeroizing;

#[derive(Debug, thiserror::Error)]
pub enum ArkSessionError {
    #[error("No active session")]
    Locked,
    #[error("Wrong password")]
    WrongPassword,
    #[error("Key derivation failed: {0}")]
    Derivation(#[source] CryptoError),
    #[error("{0}")]
    KeyWrap(#[from] CryptoError),
}

type ArkSessionResult<T> = Result<T, ArkSessionError>;

pub struct PasswordWrappedArk {
    pub salt: Vec<u8>,
    pub wrapped: Sealed<AccountRootKey>,
}

impl PasswordWrappedArk {
    /// Any unwrap failure is `WrongPassword`: AEAD cannot tell the causes apart.
    fn open(&self, password: &str, user_id: UserId) -> ArkSessionResult<AccountRootKey> {
        derive_kek(password, &self.salt)?
            .unwrap_key(&self.wrapped, &user_id)
            .map_err(|e| match e {
                CryptoError::KeyUnwrapFailed => ArkSessionError::WrongPassword,
                other => other.into(),
            })
    }
}

pub struct NewAccount {
    pub user_id: UserId,
    pub password_wrapped_ark: PasswordWrappedArk,
    pub vault_id: VaultId,
    pub wrapped_vault_key: Sealed<VaultKey>,
}

/// One account-wide record and the key that seals it, wrapped under the ARK.
pub struct AccountRecord {
    pub wrapped_key: Sealed<NamespaceKey>,
    pub data: Sealed<[u8]>,
}

#[derive(Default)]
pub struct ArkSession {
    ark: Mutex<Option<AccountRootKey>>,
}

impl ArkSession {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn end(&self) {
        self.lock().take();
    }

    pub fn is_active(&self) -> bool {
        self.lock().is_some()
    }

    pub fn create_account(&self, password: &str) -> ArkSessionResult<NewAccount> {
        let user_id = UserId::random();
        let vault_id = VaultId::random();
        let ark = AccountRootKey::generate();
        let vault_key = VaultKey::generate();

        let salt = new_salt().to_vec();
        let password_wrapped_ark = PasswordWrappedArk {
            wrapped: derive_kek(password, &salt)?.wrap_key(&ark, &user_id)?,
            salt,
        };
        let wrapped_vault_key = ark.wrap_key(&vault_key, &vault_id)?;

        *self.lock() = Some(ark);

        Ok(NewAccount {
            user_id,
            password_wrapped_ark,
            vault_id,
            wrapped_vault_key,
        })
    }

    pub fn unlock_with_password(
        &self,
        password: &str,
        wrapped: &PasswordWrappedArk,
        user_id: UserId,
    ) -> ArkSessionResult<()> {
        let ark = wrapped.open(password, user_id)?;
        *self.lock() = Some(ark);
        Ok(())
    }

    pub fn unlock_with_ark(&self, ark: &[u8]) -> ArkSessionResult<()> {
        let ark = AccountRootKey::from_bytes(ark)?;
        *self.lock() = Some(ark);
        Ok(())
    }

    pub fn export_ark(&self) -> ArkSessionResult<Zeroizing<Vec<u8>>> {
        self.with_ark(|ark| Zeroizing::new(ark.as_bytes().to_vec()))
    }

    /// Fails unless `wrapped` opens to the ARK this session already holds.
    pub fn verify_password(
        &self,
        password: &str,
        wrapped: &PasswordWrappedArk,
        user_id: UserId,
    ) -> ArkSessionResult<()> {
        let stored = wrapped.open(password, user_id)?;

        self.with_live_ark(|live| {
            if bool::from(live.as_bytes().ct_eq(stored.as_bytes())) {
                Ok(())
            } else {
                Err(ArkSessionError::WrongPassword)
            }
        })
    }

    pub fn verify_ark(&self, candidate: &[u8]) -> ArkSessionResult<bool> {
        self.with_live_ark(|ark| Ok(ark.as_bytes().ct_eq(candidate).into()))
    }

    pub fn rewrap_for_new_password(
        &self,
        new_password: &str,
        user_id: UserId,
    ) -> ArkSessionResult<PasswordWrappedArk> {
        // Argon2 is slow; derive outside the lock.
        let salt = new_salt().to_vec();
        let kek = derive_kek(new_password, &salt)?;
        let wrapped = self.with_live_ark(|ark| Ok(kek.wrap_key(ark, &user_id)?))?;
        Ok(PasswordWrappedArk { salt, wrapped })
    }

    pub fn wrap_vault_key(
        &self,
        vault_key: &VaultKey,
        vault_id: VaultId,
    ) -> ArkSessionResult<Sealed<VaultKey>> {
        self.with_live_ark(|ark| Ok(ark.wrap_key(vault_key, &vault_id)?))
    }

    pub fn unwrap_vault_key(
        &self,
        wrapped: &Sealed<VaultKey>,
        vault_id: VaultId,
    ) -> ArkSessionResult<VaultKey> {
        self.with_live_ark(|ark| Ok(ark.unwrap_key(wrapped, &vault_id)?))
    }

    /// Seals `plaintext` under a fresh key, so a record never depends on an older one opening.
    pub fn seal_record(
        &self,
        namespace: NamespaceId,
        kind: SecretKind,
        plaintext: &[u8],
    ) -> ArkSessionResult<AccountRecord> {
        let key = NamespaceKey::generate();
        let wrapped_key =
            self.with_live_ark(|ark| Ok(ark.wrap_key(&key, &NamespaceKeyAad { namespace })?))?;
        let data = key.encrypt(plaintext, &NamespaceDataAad { namespace, kind })?;
        Ok(AccountRecord { wrapped_key, data })
    }

    pub fn open_record(
        &self,
        namespace: NamespaceId,
        kind: SecretKind,
        record: &AccountRecord,
    ) -> ArkSessionResult<Vec<u8>> {
        let key = self.with_live_ark(|ark| {
            Ok(ark.unwrap_key(&record.wrapped_key, &NamespaceKeyAad { namespace })?)
        })?;
        Ok(key.decrypt(&record.data, &NamespaceDataAad { namespace, kind })?)
    }

    /// Runs `f` on a copy of the ARK outside the lock, so `f` may re-enter the session.
    pub fn with_ark<R>(&self, f: impl FnOnce(&AccountRootKey) -> R) -> ArkSessionResult<R> {
        let ark = self.with_live_ark(|live| Ok(AccountRootKey::from_bytes(live.as_bytes())?))?;
        Ok(f(&ark))
    }

    /// Runs `f` under the lock; `f` must not re-enter the session.
    fn with_live_ark<R>(
        &self,
        f: impl FnOnce(&AccountRootKey) -> ArkSessionResult<R>,
    ) -> ArkSessionResult<R> {
        f(self.lock().as_ref().ok_or(ArkSessionError::Locked)?)
    }

    fn lock(&self) -> MutexGuard<'_, Option<AccountRootKey>> {
        self.ark.lock().unwrap_or_else(|e| e.into_inner())
    }
}

fn derive_kek(password: &str, salt: &[u8]) -> ArkSessionResult<RootKEK> {
    RootKEK::from_password(password.as_bytes(), salt).map_err(ArkSessionError::Derivation)
}

#[cfg(test)]
mod tests {
    use super::*;

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
            .unlock_with_password(PASSWORD, &account.password_wrapped_ark, account.user_id)
            .unwrap();

        assert!(session.is_active());
    }

    #[test]
    fn create_account_default_vault_key_unwraps_under_the_ark() {
        let (session, account) = unlocked();

        assert!(
            session
                .unwrap_vault_key(&account.wrapped_vault_key, account.vault_id)
                .is_ok()
        );
    }

    #[test]
    fn unlock_with_wrong_password_leaves_session_locked() {
        let (session, account) = unlocked();
        session.end();

        let result =
            session.unlock_with_password("wrong", &account.password_wrapped_ark, account.user_id);

        assert!(matches!(result, Err(ArkSessionError::WrongPassword)));
        assert!(!session.is_active());
    }

    #[test]
    fn unlock_with_password_rejects_a_blob_wrapped_for_a_different_user_id() {
        let (session, account) = unlocked();
        session.end();

        let result =
            session.unlock_with_password(PASSWORD, &account.password_wrapped_ark, UserId::random());

        assert!(matches!(result, Err(ArkSessionError::WrongPassword)));
        assert!(!session.is_active());
    }

    #[test]
    fn failed_unlock_on_an_active_session_retains_the_original_ark() {
        let (session, account) = unlocked();
        let original = session.export_ark().unwrap();

        let result =
            session.unlock_with_password("wrong", &account.password_wrapped_ark, account.user_id);

        assert!(result.is_err());
        assert!(session.is_active());
        assert_eq!(session.export_ark().unwrap(), original);
    }

    #[test]
    fn export_and_unlock_with_ark_round_trip() {
        let (session, account) = unlocked();
        let exported = session.export_ark().unwrap();

        let second = ArkSession::new();
        second.unlock_with_ark(&exported).unwrap();

        let wrapped = session
            .wrap_vault_key(&VaultKey::generate(), account.vault_id)
            .unwrap();
        assert!(second.unwrap_vault_key(&wrapped, account.vault_id).is_ok());
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

        assert!(matches!(
            session.unlock_with_ark(&[0u8; 8]),
            Err(ArkSessionError::KeyWrap(_))
        ));
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
                .verify_password(PASSWORD, &wrapped, account.user_id)
                .is_ok()
        );

        let wrapped = session
            .rewrap_for_new_password(PASSWORD, account.user_id)
            .unwrap();
        assert!(matches!(
            session.verify_password("nope", &wrapped, account.user_id),
            Err(ArkSessionError::WrongPassword)
        ));
    }

    #[test]
    fn verify_password_rejects_a_blob_that_holds_a_different_ark() {
        let (session, _) = unlocked();
        let (_, other) = unlocked();

        assert!(matches!(
            session.verify_password(PASSWORD, &other.password_wrapped_ark, other.user_id),
            Err(ArkSessionError::WrongPassword)
        ));
    }

    #[test]
    fn verify_password_fails_when_locked() {
        let (session, account) = unlocked();
        session.end();

        assert!(matches!(
            session.verify_password(PASSWORD, &account.password_wrapped_ark, account.user_id),
            Err(ArkSessionError::Locked)
        ));
    }

    #[test]
    fn verify_ark_matches_only_the_live_ark() {
        let (session, _) = unlocked();
        let exported = session.export_ark().unwrap();

        assert!(session.verify_ark(&exported).unwrap());
        assert!(!session.verify_ark(&[0u8; 32]).unwrap());
    }

    #[test]
    fn verify_ark_reports_a_locked_session_rather_than_a_mismatch() {
        let (session, _) = unlocked();
        let exported = session.export_ark().unwrap();
        session.end();

        assert!(matches!(
            session.verify_ark(&exported),
            Err(ArkSessionError::Locked)
        ));
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
            .unlock_with_password("new-password", &rewrapped, account.user_id)
            .unwrap();

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
            session.rewrap_for_new_password("new-password", UserId::random()),
            Err(ArkSessionError::Locked)
        ));
    }

    const NAMESPACE: NamespaceId = NamespaceId(uuid::Uuid::from_u128(
        0x5555_5555_5555_4555_8555_5555_5555_5555,
    ));
    const KIND: SecretKind = SecretKind::PasswordHealthReport;

    #[test]
    fn a_sealed_record_opens_to_its_plaintext() {
        let (session, _) = unlocked();

        let record = session.seal_record(NAMESPACE, KIND, b"report").unwrap();

        assert_eq!(
            session.open_record(NAMESPACE, KIND, &record).unwrap(),
            b"report"
        );
    }

    #[test]
    fn every_seal_uses_a_fresh_key() {
        let (session, _) = unlocked();

        let first = session.seal_record(NAMESPACE, KIND, b"report").unwrap();
        let second = session.seal_record(NAMESPACE, KIND, b"report").unwrap();

        assert_ne!(
            first.wrapped_key.ciphertext(),
            second.wrapped_key.ciphertext()
        );
    }

    #[test]
    fn a_record_does_not_open_under_another_namespace() {
        let (session, _) = unlocked();
        let record = session.seal_record(NAMESPACE, KIND, b"report").unwrap();

        assert!(matches!(
            session.open_record(NamespaceId::random(), KIND, &record),
            Err(ArkSessionError::KeyWrap(CryptoError::KeyUnwrapFailed))
        ));
    }

    #[test]
    fn a_record_does_not_open_as_another_kind() {
        let (session, _) = unlocked();
        let record = session.seal_record(NAMESPACE, KIND, b"report").unwrap();

        assert!(matches!(
            session.open_record(NAMESPACE, SecretKind::Password, &record),
            Err(ArkSessionError::KeyWrap(CryptoError::DecryptionFailed))
        ));
    }

    #[test]
    fn a_record_does_not_open_under_another_account() {
        let (session, _) = unlocked();
        let (other, _) = unlocked();
        let record = session.seal_record(NAMESPACE, KIND, b"report").unwrap();

        assert!(matches!(
            other.open_record(NAMESPACE, KIND, &record),
            Err(ArkSessionError::KeyWrap(CryptoError::KeyUnwrapFailed))
        ));
    }

    #[test]
    fn records_need_an_unlocked_session() {
        let (session, _) = unlocked();
        let record = session.seal_record(NAMESPACE, KIND, b"report").unwrap();
        session.end();

        assert!(matches!(
            session.seal_record(NAMESPACE, KIND, b"report"),
            Err(ArkSessionError::Locked)
        ));
        assert!(matches!(
            session.open_record(NAMESPACE, KIND, &record),
            Err(ArkSessionError::Locked)
        ));
    }

    #[test]
    fn with_ark_does_not_hold_the_lock_across_the_closure() {
        let (session, _) = unlocked();

        // All take the same lock; a hang here means a deadlock.
        let reentered = session
            .with_ark(|ark| {
                let exported = session.export_ark().unwrap();
                assert_eq!(exported.as_slice(), ark.as_bytes());
                assert!(session.is_active());
                session.verify_ark(ark.as_bytes()).unwrap()
            })
            .unwrap();

        assert!(reentered);
    }

    #[test]
    fn with_ark_sees_the_ark_that_was_live_when_it_started() {
        let (session, _) = unlocked();
        let original = session.export_ark().unwrap();

        let observed = session
            .with_ark(|ark| {
                session.end();
                ark.as_bytes().to_vec()
            })
            .unwrap();

        assert_eq!(observed, *original);
        assert!(!session.is_active());
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
