use crate::domain::{ACCESS_TOKEN_LIFETIME, AccessToken, DeviceId, RefreshToken};
use crate::service::session::Issued;
use serde::Serialize;

#[derive(Serialize)]
pub struct Access {
    access_token: AccessToken,
    access_expires_in: u64,
}

impl From<AccessToken> for Access {
    fn from(access_token: AccessToken) -> Self {
        Self {
            access_token,
            access_expires_in: ACCESS_TOKEN_LIFETIME.as_secs(),
        }
    }
}

#[derive(Serialize)]
pub struct Session {
    device_id: DeviceId,
    #[serde(flatten)]
    access: Access,
    refresh_token: RefreshToken,
    refresh_expires_in: u64,
}

impl From<Issued> for Session {
    fn from(value: Issued) -> Self {
        Self {
            device_id: value.device_id,
            access: value.access_token.into(),
            refresh_token: value.refresh_token,
            refresh_expires_in: value.refresh_expires_in.as_secs(),
        }
    }
}
