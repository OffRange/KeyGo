use crate::domain::{AccessToken, DeviceId, RefreshToken};
use crate::service::session::Issued;
use serde::Serialize;

#[derive(Serialize)]
pub struct Session {
    device_id: DeviceId,
    access_token: AccessToken,
    access_expires_in: u64,
    refresh_token: RefreshToken,
    refresh_expires_in: u64,
}

impl From<Issued> for Session {
    fn from(value: Issued) -> Self {
        Self {
            device_id: value.device_id,
            access_token: value.access_token,
            access_expires_in: value.access_expires_in.as_secs(),
            refresh_token: value.refresh_token,
            refresh_expires_in: value.refresh_expires_in.as_secs(),
        }
    }
}
