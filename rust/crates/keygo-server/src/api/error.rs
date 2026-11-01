use crate::domain::UsernameError;
use axum::extract::rejection::JsonRejection;
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use keygo_opaque::OpaqueError;
use serde::Serialize;
use serde_json::json;
use thiserror::Error;

/// Every `code` the API answers with; clients match on the serialized name.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum ErrorCode {
    // general
    Internal,
    InvalidRequest,
    NotFound,
    RateLimited,
    // auth
    InvalidUsername,
    InvalidCredentials,
    UsernameTaken,
    UserIdTaken,
    InviteRequired,
    InviteInvalid,
    RegistrationExpired,
    LoginExpired,
    // session
    FreshAuthRequired,
    InvalidToken,
    RefreshExpired,
    SessionRevoked,
    // dpop
    InvalidDpopProof,
    UseDpopNonce,
}

impl ErrorCode {
    pub fn status(self) -> StatusCode {
        match self {
            Self::Internal => StatusCode::INTERNAL_SERVER_ERROR,
            Self::InvalidRequest
            | Self::InvalidUsername
            | Self::RegistrationExpired
            | Self::LoginExpired
            | Self::InvalidDpopProof
            | Self::UseDpopNonce => StatusCode::BAD_REQUEST,
            Self::InvalidCredentials
            | Self::InvalidToken
            | Self::RefreshExpired
            | Self::SessionRevoked => StatusCode::UNAUTHORIZED,
            Self::InviteRequired | Self::InviteInvalid | Self::FreshAuthRequired => {
                StatusCode::FORBIDDEN
            }
            Self::NotFound => StatusCode::NOT_FOUND,
            Self::UsernameTaken | Self::UserIdTaken => StatusCode::CONFLICT,
            Self::RateLimited => StatusCode::TOO_MANY_REQUESTS,
        }
    }
}

pub trait HttpError: std::error::Error + Send + Sync + 'static {
    fn code(&self) -> ErrorCode;

    fn status(&self) -> StatusCode {
        self.code().status()
    }

    fn headers(&self) -> HeaderMap {
        HeaderMap::new()
    }

    fn reason(&self) -> Option<&'static str> {
        None
    }
}

pub struct ServerError(Box<dyn HttpError>);

pub type ServerResult<T> = Result<T, ServerError>;

impl<E: HttpError> From<E> for ServerError {
    fn from(err: E) -> Self {
        Self(Box::new(err))
    }
}

impl IntoResponse for ServerError {
    fn into_response(self) -> Response {
        let (code, status, headers) = (self.0.code(), self.0.status(), self.0.headers());
        let reason = self.0.reason();
        let message = if status.is_server_error() {
            let source = anyhow::Error::from_boxed(self.0);
            tracing::error!(?code, "request failed: {source:#}");
            "internal server error".to_owned()
        } else {
            self.0.to_string()
        };

        let mut body = json!({"code": code, "message": message});
        if let Some(reason) = reason {
            body["reason"] = reason.into();
        }

        (status, headers, axum::Json(body)).into_response()
    }
}

#[derive(Debug, Error)]
#[error("resource not found")]
pub struct NotFound;

impl HttpError for NotFound {
    fn code(&self) -> ErrorCode {
        ErrorCode::NotFound
    }
}

impl HttpError for sqlx::Error {
    fn code(&self) -> ErrorCode {
        ErrorCode::Internal
    }
}

impl HttpError for JsonRejection {
    fn code(&self) -> ErrorCode {
        ErrorCode::InvalidRequest
    }

    fn status(&self) -> StatusCode {
        match self {
            // Malformed or mistyped JSON is a bad request like any other; a missing content type or
            // an oversized body keeps its own HTTP status.
            Self::JsonDataError(_) | Self::JsonSyntaxError(_) => StatusCode::BAD_REQUEST,
            _ => JsonRejection::status(self),
        }
    }
}

impl HttpError for UsernameError {
    fn code(&self) -> ErrorCode {
        ErrorCode::InvalidUsername
    }
}

impl HttpError for OpaqueError {
    fn code(&self) -> ErrorCode {
        match self {
            // One code for wrong password and unknown account, so a username's existence never
            // leaks.
            Self::InvalidLogin => ErrorCode::InvalidCredentials,
            Self::Malformed => ErrorCode::InvalidRequest,
            // `ReflectedValue` and `EmptyPassword` are only raised by the client half of the
            // protocol; seeing one here means server code called a client function.
            Self::ReflectedValue | Self::EmptyPassword | Self::Protocol(_) => ErrorCode::Internal,
        }
    }
}

/// Renders an error the way a handler would answer with it.
#[cfg(test)]
pub async fn render(err: impl Into<ServerError>) -> (StatusCode, serde_json::Value) {
    use http_body_util::BodyExt;

    let response = err.into().into_response();
    let status = response.status();
    let body = response.into_body().collect().await.unwrap().to_bytes();
    (status, serde_json::from_slice(&body).unwrap())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[tokio::test]
    async fn client_errors_expose_their_message() {
        let (status, body) = render(OpaqueError::InvalidLogin).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(
            body,
            json!({
                "code": "invalid_credentials",
                "message": OpaqueError::InvalidLogin.to_string(),
            }),
        );
    }

    #[tokio::test]
    async fn malformed_opaque_messages_are_invalid_requests() {
        let (status, body) = render(OpaqueError::Malformed).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_request");
    }

    #[tokio::test]
    async fn invalid_usernames_are_bad_requests() {
        let (status, body) = render(UsernameError::TooShort).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_username");
    }

    #[tokio::test]
    async fn server_errors_hide_their_message() {
        let (status, body) = render(OpaqueError::Protocol("secret detail".into())).await;

        assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
        assert_eq!(
            body,
            json!({ "code": "internal", "message": "internal server error" }),
        );
    }

    #[tokio::test]
    async fn database_errors_are_internal() {
        let (status, body) = render(sqlx::Error::RowNotFound).await;

        assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
        assert_eq!(body["code"], "internal");
    }

    #[tokio::test]
    async fn not_found_keeps_its_message() {
        let (status, body) = render(NotFound).await;

        assert_eq!(status, StatusCode::NOT_FOUND);
        assert_eq!(
            body,
            json!({ "code": "not_found", "message": "resource not found" }),
        );
    }

    #[derive(Debug, thiserror::Error)]
    #[error("challenge")]
    struct Challenge;

    impl HttpError for Challenge {
        fn code(&self) -> ErrorCode {
            ErrorCode::InvalidCredentials
        }

        fn headers(&self) -> HeaderMap {
            HeaderMap::from_iter([(
                axum::http::header::WWW_AUTHENTICATE,
                "DPoP".parse().unwrap(),
            )])
        }
    }

    #[derive(Debug, thiserror::Error)]
    #[error("revoked")]
    struct Revoked;

    impl HttpError for Revoked {
        fn code(&self) -> ErrorCode {
            ErrorCode::SessionRevoked
        }

        fn reason(&self) -> Option<&'static str> {
            Some("replaced")
        }
    }

    #[tokio::test]
    async fn error_reason_reaches_the_body() {
        let (status, body) = render(Revoked).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(
            body,
            json!({ "code": "session_revoked", "message": "revoked", "reason": "replaced" }),
        );
    }

    #[tokio::test]
    async fn errors_without_a_reason_omit_it() {
        let (_, body) = render(NotFound).await;

        assert!(body.get("reason").is_none());
    }

    #[tokio::test]
    async fn error_headers_reach_the_response() {
        let response = ServerError::from(Challenge).into_response();

        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
        assert_eq!(response.headers()["www-authenticate"], "DPoP");
    }
}
