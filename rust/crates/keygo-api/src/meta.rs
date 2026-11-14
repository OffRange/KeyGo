use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct ServerInfo {
    pub version: String,
    pub public_url: String,
    pub allow_signups: bool,
}
