use passkey::authenticator::{Authenticator, CredentialStore, UserCheck, UserValidationMethod};
use passkey::types::ctap2::{Aaguid, Ctap2Error};
use passkey::types::Passkey;
use passkey_authenticator::UiHint;

pub(crate) struct KeyGoUserValidation {}

#[async_trait::async_trait]
impl UserValidationMethod for KeyGoUserValidation {
    type PasskeyItem = Passkey;

    async fn check_user<'a>(&self, _hint: UiHint<'a, Self::PasskeyItem>, presence: bool, verification: bool) -> Result<UserCheck, Ctap2Error> {
        Ok(
            UserCheck {
                presence,
                verification,
            }
        )
    }

    fn is_presence_enabled(&self) -> bool {
        true
    }

    fn is_verification_enabled(&self) -> Option<bool> {
        Some(true)
    }
}

pub(crate) fn keygo_authenticator<S: CredentialStore>(store: S) -> Authenticator<S, KeyGoUserValidation> {
    let aaguid = Aaguid::new_empty();

    Authenticator::new(aaguid, store, KeyGoUserValidation {})
}