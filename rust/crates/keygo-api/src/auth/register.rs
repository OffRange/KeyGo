use super::Session;
use crate::domain::{DeviceInfo, KeyBundle, RegistrationId, Username};
use keygo_core::UserId;
use keygo_opaque::{RegistrationRequest, RegistrationResponse, RegistrationUpload};
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub struct StartRequest {
    /// Parsed in the handler so a bad name answers `invalid_username`, not `invalid_request`.
    pub username: String,
    pub user_id: UserId,
    pub invite: Option<String>,
    pub registration_request: RegistrationRequest,
}

#[derive(Serialize, Deserialize)]
pub struct StartResponse {
    pub registration_id: RegistrationId,
    pub registration_response: RegistrationResponse,
    pub expires_in: u64,
}

#[derive(Serialize, Deserialize)]
pub struct FinishRequest {
    pub registration_id: RegistrationId,
    pub registration_upload: RegistrationUpload,
    pub key_bundle: KeyBundle,
    pub device: DeviceInfo,
}

#[derive(Serialize, Deserialize)]
pub struct FinishResponse {
    pub username: Username,
    pub session: Session,
}
