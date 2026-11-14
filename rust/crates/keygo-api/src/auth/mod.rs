use crate::domain::{AccessToken, RefreshToken};
use keygo_core::DeviceId;
use serde::{Deserialize, Serialize};

pub mod handshake;
pub mod login;
pub mod register;
pub mod step_up;
pub mod token;

#[derive(Serialize, Deserialize)]
pub struct Access {
    pub access_token: AccessToken,
    pub access_expires_in: u64,
}

#[derive(Serialize, Deserialize)]
pub struct Session {
    pub device_id: DeviceId,
    #[serde(flatten)]
    pub access: Access,
    pub refresh_token: RefreshToken,
    pub refresh_expires_in: u64,
}
