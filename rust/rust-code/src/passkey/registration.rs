use crate::passkey::authenticator::keygo_authenticator;
use crate::passkey::keygo_passkey::to_bytes;
use crate::passkey::registration::RegistrationError::{InvalidDomain, InvalidJsonFormat};
use crate::url::sanitize_to_https_url;
use passkey::client::{Client, DefaultClientData, WebauthnError};
use passkey::types::webauthn::{CredentialCreationOptions, PublicKeyCredentialCreationOptions};
use thiserror::Error;

pub(crate) struct KeyGoRegistrationResponse {
    response: String,
    credential_id: Vec<u8>,
    private_key: Vec<u8>,
}

impl KeyGoRegistrationResponse {
    pub(crate) fn response(&self) -> &str {
        &self.response
    }

    pub(crate) fn credential_id(&self) -> &[u8] {
        &self.credential_id
    }

    pub(crate) fn private_key(&self) -> &[u8] {
        &self.private_key
    }
}

#[derive(Debug, Error)]
pub(crate) enum RegistrationError {
    #[error("invalid json format")]
    InvalidJsonFormat,
    #[error("invalid url")]
    InvalidDomain,
    #[error("webauthn error: {0:?}")]
    WebauthnError(WebauthnError),
}

pub(crate) async fn get_exclusion_list(json_request: &str) -> Result<Vec<Vec<u8>>, RegistrationError> {
    let creation_options: PublicKeyCredentialCreationOptions = serde_json::from_str(json_request)
        .map_err(|_| InvalidJsonFormat)?;

    let list = creation_options.exclude_credentials.unwrap_or_default();
    let ids = list.iter()
        .map(|desc| desc.id.clone().into())
        .collect();

    Ok(ids)
}

pub(crate) async fn register_passkey(json_request: &str) -> Result<KeyGoRegistrationResponse, RegistrationError> {
    let creation_options: PublicKeyCredentialCreationOptions = serde_json::from_str(json_request)
        .map_err(|_| InvalidJsonFormat)?;

    let domain = creation_options.rp.id.as_deref().unwrap_or_default();

    let authenticator = keygo_authenticator();
    let mut client = Client::new(authenticator);

    let domain = sanitize_to_https_url(domain).map_err(|_| InvalidDomain)?;
    let options = CredentialCreationOptions { public_key: creation_options };
    let pub_key_credential = client.register(domain, options, DefaultClientData)
        .await
        .map_err(RegistrationError::WebauthnError)?;
    let response_json = serde_json::to_string(&pub_key_credential).unwrap();

    let response = client.authenticator().store()
        .clone()
        .unwrap();

    let credential_id = response.credential_id.clone().into();
    let keygo_registration_response = KeyGoRegistrationResponse {
        response: response_json,
        credential_id,
        private_key: to_bytes(response),
    };

    Ok(keygo_registration_response)
}