use passkey::authenticator::{Authenticator, CredentialStore, UserCheck, UserValidationMethod};
use passkey::types::ctap2::{Aaguid, Ctap2Error};
use passkey::types::Passkey;

pub(crate) struct KeyGoUserValidation {}

#[async_trait::async_trait]
impl UserValidationMethod for KeyGoUserValidation {
    type PasskeyItem = Passkey;

    #[allow(clippy::needless_lifetimes)]
    async fn check_user<'a>(
        &self,
        _credential: Option<&'a Self::PasskeyItem>,
        _presence: bool,
        _verification: bool,
    ) -> Result<UserCheck, Ctap2Error> {
        Ok(
            UserCheck {
                presence: true,
                verification: true,
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