use crate::domain::LoginId;
use keygo_opaque::CredentialResponse;
use serde::{Deserialize, Serialize};

/// The first answer of an OPAQUE login, shared by sign-in and step-up.
#[derive(Serialize, Deserialize)]
pub struct StartResponse {
    pub login_id: LoginId,
    pub credential_response: CredentialResponse,
    pub expires_in: u64,
}
