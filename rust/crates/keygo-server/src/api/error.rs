use crate::domain::UsernameError;
use axum::extract::rejection::JsonRejection;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use keygo_opaque::OpaqueError;
use serde::Serialize;
use serde_json::json;

const INTERNAL: (StatusCode, ErrorCode) = (StatusCode::INTERNAL_SERVER_ERROR, ErrorCode::Internal);

/// Every `code` the API answers with; clients match on the serialized name.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum ErrorCode {
    // general
    Internal,
    InvalidRequest,
    NotFound,
    // auth
    InvalidUsername,
    InvalidCredentials,
    UsernameTaken,
    UserIdTaken,
    InviteRequired,
    InviteInvalid,
    RegistrationExpired,
    LoginExpired,
}

pub trait HttpError: std::error::Error + Send + Sync + 'static {
    fn status_and_code(&self) -> (StatusCode, ErrorCode);
}

pub struct ServerError {
    status: StatusCode,
    code: ErrorCode,
    message: String,
    source: Option<anyhow::Error>,
}

pub type ServerResult<T> = Result<T, ServerError>;

impl ServerError {
    pub fn new(status: StatusCode, code: ErrorCode, message: impl Into<String>) -> Self {
        Self {
            status,
            code,
            message: message.into(),
            source: None,
        }
    }

    pub fn not_found() -> Self {
        Self::new(
            StatusCode::NOT_FOUND,
            ErrorCode::NotFound,
            "resource not found",
        )
    }

    fn internal(source: anyhow::Error) -> Self {
        Self {
            status: StatusCode::INTERNAL_SERVER_ERROR,
            code: ErrorCode::Internal,
            message: "internal server error".to_owned(),
            source: Some(source),
        }
    }
}

impl<E: HttpError> From<E> for ServerError {
    fn from(err: E) -> Self {
        let (status, code) = err.status_and_code();
        if status.is_server_error() {
            return Self {
                status,
                code,
                ..Self::internal(err.into())
            };
        }

        Self::new(status, code, err.to_string())
    }
}

impl From<anyhow::Error> for ServerError {
    fn from(err: anyhow::Error) -> Self {
        Self::internal(err)
    }
}

impl IntoResponse for ServerError {
    fn into_response(self) -> Response {
        if let Some(source) = &self.source {
            tracing::error!(code = ?self.code, "request failed: {source:#}");
        }

        (
            self.status,
            axum::Json(json!({"code": self.code, "message": self.message})),
        )
            .into_response()
    }
}

impl HttpError for sqlx::Error {
    fn status_and_code(&self) -> (StatusCode, ErrorCode) {
        INTERNAL
    }
}

impl HttpError for JsonRejection {
    fn status_and_code(&self) -> (StatusCode, ErrorCode) {
        let status = match self {
            // Malformed or mistyped JSON is a bad request like any other (§15); a missing content
            // type or an oversized body keeps its own HTTP status.
            Self::JsonDataError(_) | Self::JsonSyntaxError(_) => StatusCode::BAD_REQUEST,
            _ => self.status(),
        };
        (status, ErrorCode::InvalidRequest)
    }
}

impl HttpError for UsernameError {
    fn status_and_code(&self) -> (StatusCode, ErrorCode) {
        (StatusCode::BAD_REQUEST, ErrorCode::InvalidUsername)
    }
}

impl HttpError for OpaqueError {
    fn status_and_code(&self) -> (StatusCode, ErrorCode) {
        match self {
            // One code for wrong password and unknown account, so a username's existence never
            // leaks.
            Self::InvalidLogin => (StatusCode::UNAUTHORIZED, ErrorCode::InvalidCredentials),
            Self::Malformed => (StatusCode::BAD_REQUEST, ErrorCode::InvalidRequest),
            // `ReflectedValue` and `EmptyPassword` are only raised by the client half of the
            // protocol; seeing one here means server code called a client function.
            Self::ReflectedValue | Self::EmptyPassword | Self::Protocol(_) => INTERNAL,
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
    async fn anyhow_errors_are_internal() {
        let (status, body) = render(anyhow::anyhow!("secret detail").context("loading")).await;

        assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
        assert_eq!(body["code"], "internal");
        assert_eq!(body["message"], "internal server error");
    }

    #[tokio::test]
    async fn database_errors_are_internal() {
        let (status, body) = render(sqlx::Error::RowNotFound).await;

        assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
        assert_eq!(body["code"], "internal");
    }

    #[tokio::test]
    async fn ad_hoc_errors_keep_status_code_and_message() {
        let (status, body) = render(ServerError::not_found()).await;

        assert_eq!(status, StatusCode::NOT_FOUND);
        assert_eq!(
            body,
            json!({ "code": "not_found", "message": "resource not found" }),
        );
    }
}
