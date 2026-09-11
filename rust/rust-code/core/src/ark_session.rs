use crate::crypto::error::CryptoError;
use crate::crypto::primitive::argon2::MIN_SALT_LEN;
use crate::crypto::primitive::wrap_key::{AeadWrappedKey, KeyWrapper};
use crate::crypto::random::random_bytes;
use crate::crypto::types::{UserId, VaultId};
use crate::crypto::{AccountRootKey, KeyMaterial, RootKEK, TryDeriveFrom, VaultKey};
use std::sync::Mutex;
use subtle::ConstantTimeEq;
use zeroize::Zeroizing;

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

    fn unlock(
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
    ///
    /// Replaces any ARK already in the session. That is deliberate and safe: the
    /// displaced [`AccountRootKey`] is `ZeroizeOnDrop`, so it is wiped on assignment.
    /// The only risk is logical, a caller silently swapping the session's identity, and
    /// both callers are gated by the flow they belong to.
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

    /// Preserves `KeyWrap` rather than collapsing it, unlike `verify_password`, so the caller can
    /// tell a wrong password apart from a corrupt blob.
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

    /// Take custody of an ARK recovered outside Rust. The only door that takes custody of one
    /// from the JVM (`verify_ark` also accepts ARK bytes, but only to compare them): the biometric
    /// unlock and the backup escrow both hold their copy under an Android Keystore key, which
    /// only exists on the JVM side.
    ///
    /// Replaces any ARK already in the session. That is deliberate and safe: the
    /// displaced [`AccountRootKey`] is `ZeroizeOnDrop`, so it is wiped on assignment.
    /// The only risk is logical, a caller silently swapping the session's identity, and
    /// both callers are gated by the flow they belong to.
    pub fn unlock_with_ark(&self, ark: &[u8]) -> ArkSessionResult<()> {
        let ark = AccountRootKey::try_from_bytes(ark)?;
        *self.lock() = Some(ark);
        Ok(())
    }

    pub fn export_ark(&self) -> ArkSessionResult<Zeroizing<Vec<u8>>> {
        self.with_ark(|ark| Zeroizing::new(ark.as_bytes().to_vec()))
    }

    pub fn verify_password(
        &self,
        password: &str,
        salt: &[u8],
        wrapped: AeadWrappedKey<AccountRootKey, RootKEK>,
        user_id: UserId,
    ) -> ArkSessionResult<()> {
        let kek = derive_kek(password, salt)?;
        let stored = kek
            .unwrap_key(&wrapped, &user_id)
            .map_err(|_| ArkSessionError::WrongPassword)?;

        let guard = self.lock();
        let live = guard.as_ref().ok_or(ArkSessionError::Locked)?;
        if bool::from(live.as_bytes().ct_eq(stored.as_bytes())) {
            Ok(())
        } else {
            Err(ArkSessionError::WrongPassword)
        }
    }

    pub fn verify_ark(&self, candidate: &[u8]) -> ArkSessionResult<bool> {
        let guard = self.lock();
        let ark = guard.as_ref().ok_or(ArkSessionError::Locked)?;
        Ok(ark.as_bytes().ct_eq(candidate).into())
    }

    pub fn rewrap_for_new_password(
        &self,
        new_password: &str,
        user_id: UserId,
    ) -> ArkSessionResult<PasswordWrapped> {
        let salt = random_bytes::<SALT_LEN>().to_vec();
        let kek = derive_kek(new_password, &salt)?;

        let guard = self.lock();
        let ark = guard.as_ref().ok_or(ArkSessionError::Locked)?;
        let wrapped = kek.wrap_key(ark, &user_id)?;

        Ok(PasswordWrapped { salt, wrapped })
    }

    /// Borrow the ARK for the length of `f`. Lets callers inside Rust use the ARK without it
    /// ever being copied out of Rust.
    ///
    /// `f` runs on a private clone, taken while the lock is held and released before `f` starts.
    /// Holding the lock across `f` would be simpler, but `f` is an arbitrary caller-supplied
    /// closure: sealing a backup runs a full serialization and one AEAD pass over an entire vault
    /// under it. Every other session operation takes the same lock, `end()` among them, and
    /// `end()` is called from the lock observer on the Android main thread. A long `f` would
    /// block auto-lock there for as long as it ran.
    ///
    /// The clone is an [`AccountRootKey`], so it is zeroized when it drops at the end of this
    /// call, and it never leaves Rust. Cloning also makes `f` reentrant: it may call back into
    /// this session, which under a held lock would have deadlocked.
    pub fn with_ark<R>(&self, f: impl FnOnce(&AccountRootKey) -> R) -> ArkSessionResult<R> {
        let ark = {
            let guard = self.lock();
            let live = guard.as_ref().ok_or(ArkSessionError::Locked)?;
            AccountRootKey::try_from_bytes(live.as_bytes())?
        };

        Ok(f(&ark))
    }
}

fn derive_kek(password: &str, salt: &[u8]) -> ArkSessionResult<RootKEK> {
    RootKEK::try_derive_from(password.as_bytes(), salt, PASSWORD_DOMAIN)
        .map_err(|e| ArkSessionError::Derivation(format!("{e}")))
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
    fn unlock_with_password_rejects_a_blob_wrapped_for_a_different_user_id() {
        let (session, account) = unlocked();
        session.end();

        // The blob was wrapped with account.user_id as AAD; unlocking with a different id must
        // fail the AEAD tag check, the same binding that stops a blob transplant between users.
        let result = session.unlock_with_password(
            PASSWORD,
            &account.salt,
            account.password_wrapped_ark,
            UserId::new_v4(),
        );

        assert!(matches!(result, Err(ArkSessionError::KeyWrap(_))));
        assert!(!session.is_active());
    }

    #[test]
    fn failed_unlock_on_an_active_session_retains_the_original_ark() {
        let (session, account) = unlocked();
        let original = session.export_ark().unwrap();

        let result = session.unlock_with_password(
            "wrong",
            &account.salt,
            account.password_wrapped_ark,
            account.user_id,
        );

        // A failed unlock attempt must not log the user out: the session stays active and keeps
        // holding the ARK it had before the attempt.
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
    fn verify_password_rejects_a_blob_that_holds_a_different_ark() {
        let (session, _) = unlocked();
        // Same password, another account: the blob opens, but around a key this session does not
        // hold. Rewrapping after this would put the new password around the wrong ARK.
        let (_, other) = unlocked();

        assert!(matches!(
            session.verify_password(
                PASSWORD,
                &other.salt,
                other.password_wrapped_ark,
                other.user_id,
            ),
            Err(ArkSessionError::WrongPassword)
        ));
    }

    #[test]
    fn verify_password_fails_when_locked() {
        let (session, account) = unlocked();
        session.end();

        assert!(matches!(
            session.verify_password(
                PASSWORD,
                &account.salt,
                account.password_wrapped_ark,
                account.user_id,
            ),
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

    /// Known-answer test: pins `PASSWORD_DOMAIN` together with the Argon2 cost profile and the
    /// derived key length behind it. Every shipped account's ARK is wrapped under a KEK derived
    /// with this exact domain and parameter set, so if any of them drift, no existing account's
    /// password can unlock it again. This is the tripwire for that.
    #[test]
    fn password_domain_is_pinned() {
        assert_eq!(PASSWORD_DOMAIN, b"v1:kek/pwd");

        const SALT: [u8; 16] = [7; 16];
        let kek = derive_kek("hunter2", &SALT).unwrap();

        assert_eq!(
            kek.as_bytes(),
            &[
                243, 77, 26, 134, 177, 95, 102, 67, 54, 167, 232, 38, 115, 170, 132, 28, 98, 29,
                146, 108, 157, 245, 225, 131, 93, 9, 236, 235, 207, 6, 219, 103,
            ][..]
        );
    }

    #[test]
    fn with_ark_does_not_hold_the_lock_across_the_closure() {
        let (session, _) = unlocked();

        // Every one of these takes the same lock. Under a lock held across the closure they would
        // all deadlock rather than fail, so this test hanging is itself the regression signal.
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

        // Ending the session mid-closure is the case the clone exists for: `f` keeps working on
        // the key it was handed instead of reading a slot that is now empty.
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
