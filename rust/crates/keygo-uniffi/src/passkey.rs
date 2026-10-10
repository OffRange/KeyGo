use keygo_core::passkey::{
    self as core, CreationInfo, PasskeyError as CorePasskeyError, Registration,
};
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

impl From<CorePasskeyError> for PasskeyError {
    fn from(value: CorePasskeyError) -> Self {
        match value {
            CorePasskeyError::InvalidRequest(_) => Self::InvalidJsonFormat,
            CorePasskeyError::InvalidDomain => Self::InvalidDomain,
            CorePasskeyError::Webauthn(e) => Self::Webauthn {
                reason: format!("{e:?}"),
            },
            other @ (CorePasskeyError::Codec(_)
            | CorePasskeyError::ResponseEncoding(_)
            | CorePasskeyError::NoCredentialCreated) => Self::KeyCodec {
                reason: other.to_string(),
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

impl From<Registration> for RegistrationResponse {
    fn from(value: Registration) -> Self {
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

impl From<CreationInfo> for PasskeyInformation {
    fn from(value: CreationInfo) -> Self {
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
        Ok(core::register(&json_request).await?.into())
    }

    pub fn passkey_information(
        &self,
        json_request: String,
    ) -> Result<PasskeyInformation, PasskeyError> {
        Ok(core::creation_info(&json_request)?.into())
    }

    pub async fn authenticate(
        &self,
        json_request: String,
        passkey: Vec<u8>,
        client_data_hash: Option<Vec<u8>>,
    ) -> Result<String, PasskeyError> {
        Ok(core::authenticate(&json_request, &passkey, client_data_hash).await?)
    }
}
