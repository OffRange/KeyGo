use crate::domain::RefreshToken;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub struct TokenRequest {
    pub refresh_token: RefreshToken,
}
