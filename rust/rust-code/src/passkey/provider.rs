use crate::passkey::authenticator::keygo_authenticator;
use crate::passkey::keygo_passkey::passkey_from_bytes;
use crate::url::sanitize_to_https_url;
use bincode::error::DecodeError;
use passkey::client::{Client, WebauthnError};
use passkey_types::webauthn::{CredentialRequestOptions, PublicKeyCredentialRequestOptions};
use thiserror::Error;

#[derive(Debug, Error)]
pub(crate) enum ProviderError {
    #[error("invalid json format")]
    InvalidJsonFormat,
    #[error("invalid url")]
    InvalidDomain,
    #[error("webauthn error: {0:?}")]
    WebauthnError(WebauthnError),
    #[error("passkey decode error: {0:?}")]
    PasskeyDecodeError(DecodeError),
}

pub(crate) async fn provide_passkey(json_request: &str, passkey: &[u8], client_data_hash: Option<Vec<u8>>) -> Result<String, ProviderError> {
    let request_options: PublicKeyCredentialRequestOptions = serde_json::from_str(json_request)
        .map_err(|_| ProviderError::InvalidJsonFormat)?;

    let passkey = passkey_from_bytes(passkey).map_err(ProviderError::PasskeyDecodeError)?;

    let authenticator = keygo_authenticator(Some(passkey));
    let mut client = Client::new(authenticator);

    let domain = request_options.rp_id.as_deref().unwrap_or_default();
    let domain = sanitize_to_https_url(domain).map_err(|_| ProviderError::InvalidDomain)?;

    let options = CredentialRequestOptions { public_key: request_options };

    let credential = client.authenticate(domain, options, client_data_hash)
        .await
        .map_err(ProviderError::WebauthnError)?;

    let response_json = serde_json::to_string(&credential).unwrap();

    Ok(response_json)
}