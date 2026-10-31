use crate::api::{ErrorCode, HttpError};
use crate::domain::LoginId;
use crate::service::handshake;
use keygo_opaque::CredentialResponse;
use serde::Serialize;

// Sign-in and step-up run the same OPAQUE login, so they share its error codes and bodies.
impl HttpError for handshake::Error {
    fn code(&self) -> ErrorCode {
        match self {
            Self::Expired => ErrorCode::LoginExpired,
            Self::Opaque(err) => err.code(),
            Self::Db(_) => ErrorCode::Internal,
        }
    }
}

#[derive(Serialize)]
pub struct StartResponse {
    login_id: LoginId,
    credential_response: CredentialResponse,
    expires_in: u64,
}

impl From<handshake::Started> for StartResponse {
    fn from(started: handshake::Started) -> Self {
        Self {
            login_id: started.login_id,
            credential_response: started.credential_response,
            expires_in: started.expires_in.as_secs(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::api::error::render;
    use axum::http::StatusCode;
    use keygo_opaque::OpaqueError;

    #[tokio::test]
    async fn expired_logins_are_bad_requests() {
        let (status, body) = render(handshake::Error::Expired).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "login_expired");
    }

    #[tokio::test]
    async fn failed_login_finish_matches_failed_start() {
        let (status, body) = render(handshake::Error::Opaque(OpaqueError::InvalidLogin)).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_credentials");
    }
}
