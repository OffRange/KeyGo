use crate::domain::RefreshToken;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub struct TokenRequest<T = RefreshToken> {
    pub refresh_token: T,
}
