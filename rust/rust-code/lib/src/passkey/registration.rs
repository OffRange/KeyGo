use crate::passkey::authenticator::keygo_authenticator;
use crate::passkey::keygo_passkey::{PasskeyCodecError, to_bytes};
use crate::passkey::registration::RegistrationError::{
    InvalidDomain, InvalidJsonFormat, KeyEncodeError,
};
use crate::url::sanitize_to_https_url;
use passkey::client::{Client, DefaultClientData, WebauthnError};
use passkey::types::webauthn::{CredentialCreationOptions, PublicKeyCredentialCreationOptions};
use thiserror::Error;

pub struct KeyGoRegistrationResponse {
    pub response: String,
    pub user_name: String,
    pub user_display_name: String,
    pub credential_id: Vec<u8>,
    pub private_key: Vec<u8>,
    pub rp: String,
}

#[derive(Debug, Error)]
pub enum RegistrationError {
    #[error("invalid json format")]
    InvalidJsonFormat,
    #[error("invalid url")]
    InvalidDomain,
    #[error("webauthn error: {0:?}")]
    WebauthnError(WebauthnError),
    #[error("key encode error: {0:?}")]
    KeyEncodeError(PasskeyCodecError),
}

pub async fn get_exclusion_list(json_request: &str) -> Result<Vec<Vec<u8>>, RegistrationError> {
    let creation_options: PublicKeyCredentialCreationOptions =
        serde_json::from_str(json_request).map_err(|_| InvalidJsonFormat)?;

    let list = creation_options.exclude_credentials.unwrap_or_default();
    let ids = list.iter().map(|desc| desc.id.clone().into()).collect();

    Ok(ids)
}

pub async fn register_passkey(
    json_request: &str,
) -> Result<KeyGoRegistrationResponse, RegistrationError> {
    let creation_options: PublicKeyCredentialCreationOptions =
        serde_json::from_str(json_request).map_err(|_| InvalidJsonFormat)?;

    let domain = creation_options.rp.id.as_deref().unwrap_or_default();
    let user_name = creation_options.rp.name.clone();
    let user_display_name = creation_options.user.display_name.clone();

    let authenticator = keygo_authenticator(None);
    let mut client = Client::new(authenticator);

    let domain = sanitize_to_https_url(domain).map_err(|_| InvalidDomain)?;
    let options = CredentialCreationOptions {
        public_key: creation_options,
    };
    let pub_key_credential = client
        .register(domain, options, DefaultClientData)
        .await
        .map_err(RegistrationError::WebauthnError)?;
    let response_json = serde_json::to_string(&pub_key_credential).unwrap();

    let response = client.authenticator().store().clone().unwrap();

    let credential_id = response.credential_id.clone().into();
    Ok(KeyGoRegistrationResponse {
        response: response_json,
        user_name,
        user_display_name,
        credential_id,
        rp: response.rp_id.clone(),
        private_key: to_bytes(response).map_err(KeyEncodeError)?,
    })
}
