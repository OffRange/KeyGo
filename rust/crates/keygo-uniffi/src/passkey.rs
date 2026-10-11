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
            CorePasskeyError::InvalidDomain | CorePasskeyError::RpMismatch => Self::InvalidDomain,
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

#[uniffi::remote(Record)]
pub struct Registration {
    pub response: String,
    pub user_name: String,
    pub user_display_name: String,
    pub credential_id: Vec<u8>,
    pub private_key: Vec<u8>,
    pub rp: String,
}

#[uniffi::remote(Record)]
pub struct CreationInfo {
    pub exclude_credentials: Vec<Vec<u8>>,
    pub rp: String,
}

#[derive(uniffi::Object)]
pub struct RustPasskey;

#[uniffi::export(async_runtime = "tokio")]
impl RustPasskey {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub async fn register(&self, json_request: String) -> Result<Registration, PasskeyError> {
        Ok(core::register(&json_request).await?)
    }

    pub fn passkey_information(&self, json_request: String) -> Result<CreationInfo, PasskeyError> {
        Ok(core::creation_info(&json_request)?)
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
