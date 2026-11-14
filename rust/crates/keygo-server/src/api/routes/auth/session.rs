use crate::domain::{ACCESS_TOKEN_LIFETIME, AccessToken};
use crate::service::session::Issued;
use keygo_api::auth::{Access, Session};

pub fn access(access_token: AccessToken) -> Access {
    Access {
        access_token,
        access_expires_in: ACCESS_TOKEN_LIFETIME.as_secs(),
    }
}

impl From<Issued> for Session {
    fn from(value: Issued) -> Self {
        Self {
            device_id: value.device_id,
            access: access(value.access_token),
            refresh_token: value.refresh_token,
            refresh_expires_in: value.refresh_expires_in.as_secs(),
        }
    }
}
