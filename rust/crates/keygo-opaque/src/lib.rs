#![forbid(unsafe_code)]

#[macro_use]
mod macros;

pub mod client;
pub mod server;

mod dpop_binding;
mod error;
mod keys;
mod messages;
mod suite;

pub use dpop_binding::DpopBinding;
pub use error::{OpaqueError, OpaqueResult};
pub use keys::{ExportKey, KEY_LEN, SessionKey};
pub use messages::{
    CredentialFinalization, CredentialRequest, CredentialResponse, PasswordFile,
    RegistrationRequest, RegistrationResponse, RegistrationUpload,
};
pub use suite::{KSF_ITERS, KSF_LANES, KSF_MEM_KIB, LOGIN_CONTEXT, SUITE_ID};
