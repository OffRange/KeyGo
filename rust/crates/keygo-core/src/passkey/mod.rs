mod authenticator;
mod keygo_passkey;
mod provider;
mod registration;

pub use keygo_passkey::PasskeyCodecError;
pub use provider::{ProviderError, provide_passkey};
pub use registration::{
    KeyGoRegistrationResponse, PasskeyInformation, RegistrationError, get_passkey_information,
    register_passkey,
};
