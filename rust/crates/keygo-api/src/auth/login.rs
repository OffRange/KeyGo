use super::Session;
use crate::domain::{DeviceClaim, KeyBundle, LoginId, Username};
use keygo_core::UserId;
use keygo_opaque::{CredentialFinalization, CredentialRequest, DpopBinding};
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub struct StartRequest {
    /// Parsed in the handler so a bad name answers `invalid_username`, not `invalid_request`.
    pub username: String,
    pub credential_request: CredentialRequest,
}

#[derive(Serialize, Deserialize)]
pub struct FinishRequest {
    pub login_id: LoginId,
    pub credential_finalization: CredentialFinalization,
    pub device: DeviceClaim,
    pub dpop_binding: DpopBinding,
}

#[derive(Serialize, Deserialize)]
pub struct FinishResponse {
    pub user_id: UserId,
    pub username: Username,
    pub key_bundle: KeyBundle,
    pub session: Session,
}
