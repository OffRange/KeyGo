use super::Access;
use crate::domain::LoginId;
use keygo_opaque::{CredentialFinalization, CredentialRequest, DpopBinding};
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub struct StartRequest {
    pub credential_request: CredentialRequest,
}

#[derive(Serialize, Deserialize)]
pub struct FinishRequest {
    pub login_id: LoginId,
    pub credential_finalization: CredentialFinalization,
    pub dpop_binding: DpopBinding,
}

#[derive(Serialize, Deserialize)]
pub struct FinishResponse {
    #[serde(flatten)]
    pub access: Access,
    pub auth_time: u64,
}
