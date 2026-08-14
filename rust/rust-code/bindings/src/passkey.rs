use lib::passkey::provider::{ProviderError, provide_passkey};
use lib::passkey::registration::{KeyGoRegistrationResponse, PasskeyInformation as CorePasskeyInformation, RegistrationError, get_passkey_information, register_passkey};
use std::sync::Arc;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum PasskeyError {
    #[error("invalid json format")]
    InvalidJsonFormat,
    #[error("invalid url")]
    InvalidDomain,
    #[error("webauthn error: {reason}")]
    Webauthn { reason: String },
    #[error("key codec error: {reason}")]
    KeyCodec { reason: String },
}

impl From<RegistrationError> for PasskeyError {
    fn from(value: RegistrationError) -> Self {
        match value {
            RegistrationError::InvalidJsonFormat => Self::InvalidJsonFormat,
            RegistrationError::InvalidDomain => Self::InvalidDomain,
            RegistrationError::WebauthnError(e) => Self::Webauthn {
                reason: format!("{e:?}"),
            },
            RegistrationError::KeyEncodeError(e) => Self::KeyCodec {
                reason: format!("{e:?}"),
            },
        }
    }
}

impl From<ProviderError> for PasskeyError {
    fn from(value: ProviderError) -> Self {
        match value {
            ProviderError::InvalidJsonFormat => Self::InvalidJsonFormat,
            ProviderError::InvalidDomain => Self::InvalidDomain,
            ProviderError::WebauthnError(e) => Self::Webauthn {
                reason: format!("{e:?}"),
            },
            ProviderError::PasskeyDecodeError(e) => Self::KeyCodec {
                reason: format!("{e:?}"),
            },
        }
    }
}

#[derive(uniffi::Record)]
pub struct RegistrationResponse {
    pub response: String,
    pub user_name: String,
    pub user_display_name: String,
    pub credential_id: Vec<u8>,
    pub private_key: Vec<u8>,
    pub rp: String,
}

impl From<KeyGoRegistrationResponse> for RegistrationResponse {
    fn from(value: KeyGoRegistrationResponse) -> Self {
        Self {
            response: value.response,
            user_name: value.user_name,
            user_display_name: value.user_display_name,
            credential_id: value.credential_id,
            private_key: value.private_key,
            rp: value.rp,
        }
    }
}

#[derive(uniffi::Record)]
pub struct PasskeyInformation {
    pub exclude_credentials: Vec<Vec<u8>>,
    pub rp: String,
}

impl From<CorePasskeyInformation> for PasskeyInformation {
    fn from(value: CorePasskeyInformation) -> Self {
        Self {
            exclude_credentials: value.exclude_credentials,
            rp: value.rp,
        }
    }
}

#[derive(uniffi::Object)]
pub struct RustPasskey;

#[uniffi::export(async_runtime = "tokio")]
impl RustPasskey {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub async fn register(
        &self,
        json_request: String,
    ) -> Result<RegistrationResponse, PasskeyError> {
        register_passkey(&json_request)
            .await
            .map(Into::into)
            .map_err(Into::into)
    }

    pub fn passkey_information(&self, json_request: String) -> Result<PasskeyInformation, PasskeyError> {
        get_passkey_information(&json_request).map(Into::into).map_err(Into::into)
    }

    pub async fn authenticate(
        &self,
        json_request: String,
        passkey: Vec<u8>,
        client_data_hash: Option<Vec<u8>>,
    ) -> Result<String, PasskeyError> {
        provide_passkey(&json_request, &passkey, client_data_hash)
            .await
            .map_err(Into::into)
    }
}
