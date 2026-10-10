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

pub struct PasskeyInformation {
    pub exclude_credentials: Vec<Vec<u8>>,
    pub rp: String,
}

pub fn get_passkey_information(
    json_request: &str,
) -> Result<PasskeyInformation, RegistrationError> {
    let creation_options: PublicKeyCredentialCreationOptions =
        serde_json::from_str(json_request).map_err(|_| InvalidJsonFormat)?;

    let exclude_credentials: Vec<Vec<u8>> = creation_options
        .exclude_credentials
        .unwrap_or_default()
        .into_iter()
        .map(|desc| desc.id.into())
        .collect();
    let rp = creation_options.rp.id.unwrap_or_default();

    Ok(PasskeyInformation {
        exclude_credentials,
        rp,
    })
}

pub async fn register_passkey(
    json_request: &str,
) -> Result<KeyGoRegistrationResponse, RegistrationError> {
    let creation_options: PublicKeyCredentialCreationOptions =
        serde_json::from_str(json_request).map_err(|_| InvalidJsonFormat)?;

    let domain = creation_options.rp.id.as_deref().unwrap_or_default();
    let user_name = creation_options.user.name.clone();
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

#[cfg(test)]
mod tests {
    use super::*;

    const REQUEST: &str = r#"{
        "rp": { "id": "example.com", "name": "Example Corp" },
        "user": { "id": "AQIDBA", "name": "alice@example.com", "displayName": "Alice" },
        "challenge": "AAECAwQFBgcICQoLDA0ODw",
        "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }]
    }"#;

    #[tokio::test]
    async fn registration_reports_the_users_name_not_the_relying_partys() {
        let registration = register_passkey(REQUEST).await.unwrap();

        assert_eq!(registration.user_name, "alice@example.com");
        assert_eq!(registration.user_display_name, "Alice");
        assert_eq!(registration.rp, "example.com");
    }
}
