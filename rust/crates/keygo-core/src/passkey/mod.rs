//! WebAuthn registration and authentication, backed by the `passkey` crate's software
//! authenticator. The user is verified on the Kotlin side before either call is made.

mod authentication;
mod authenticator;
mod codec;
mod registration;

pub use authentication::authenticate;
pub use codec::CodecError;
pub use registration::{CreationInfo, Registration, creation_info, register};

use crate::url::sanitize_to_https_url;
use passkey::client::WebauthnError;
use thiserror::Error;
use url::Url;

#[derive(Debug, Error)]
pub enum PasskeyError {
    #[error("invalid request json: {0}")]
    InvalidRequest(#[source] serde_json::Error),
    #[error("invalid relying party domain")]
    InvalidDomain,
    #[error("webauthn error: {0:?}")]
    Webauthn(WebauthnError),
    #[error("passkey codec error: {0}")]
    Codec(#[from] CodecError),
    #[error("could not encode the response: {0}")]
    ResponseEncoding(#[source] serde_json::Error),
    #[error("the authenticator stored no credential")]
    NoCredentialCreated,
}

fn parse_request<T: serde::de::DeserializeOwned>(json: &str) -> Result<T, PasskeyError> {
    serde_json::from_str(json).map_err(PasskeyError::InvalidRequest)
}

/// The origin a request is made for, from the relying party id it names.
fn origin(rp_id: Option<&str>) -> Result<Url, PasskeyError> {
    sanitize_to_https_url(rp_id.unwrap_or_default()).map_err(|_| PasskeyError::InvalidDomain)
}

fn to_json(response: &impl serde::Serialize) -> Result<String, PasskeyError> {
    serde_json::to_string(response).map_err(PasskeyError::ResponseEncoding)
}
