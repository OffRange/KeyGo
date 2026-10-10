use passkey::authenticator::{Authenticator, CredentialStore, UserCheck, UserValidationMethod};
use passkey::crypto::rust_crypto::RustCryptoBackend;
use passkey::types::Passkey;
use passkey::types::ctap2::{Aaguid, Ctap2Error};
use passkey_authenticator::UiHint;

/// Reports presence and verification as requested: the app has already verified the user by the
/// time a request reaches Rust.
pub(super) struct PreVerifiedUser;

#[async_trait::async_trait]
impl UserValidationMethod for PreVerifiedUser {
    type PasskeyItem = Passkey;

    async fn check_user<'a>(
        &self,
        _hint: UiHint<'a, Self::PasskeyItem>,
        presence: bool,
        verification: bool,
    ) -> Result<UserCheck, Ctap2Error> {
        Ok(UserCheck {
            presence,
            verification,
        })
    }

    fn is_presence_enabled(&self) -> bool {
        true
    }

    fn is_verification_enabled(&self) -> Option<bool> {
        Some(true)
    }
}

pub(super) fn authenticator<S: CredentialStore>(
    store: S,
) -> Authenticator<S, PreVerifiedUser, RustCryptoBackend> {
    Authenticator::new(
        Aaguid::new_empty(),
        store,
        PreVerifiedUser,
        RustCryptoBackend,
    )
}
