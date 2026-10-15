use crate::domain::UsernameError;
use crate::service::{login, registration};
use axum::extract::rejection::JsonRejection;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use keygo_opaque::OpaqueError;
use serde_json::json;

pub trait HttpError: std::error::Error + Send + Sync + 'static {
    fn status(&self) -> StatusCode {
        StatusCode::INTERNAL_SERVER_ERROR
    }

    fn code(&self) -> &'static str {
        "internal"
    }
}

pub struct ServerError {
    status: StatusCode,
    code: &'static str,
    message: String,
    source: Option<anyhow::Error>,
}

pub type ServerResult<T> = Result<T, ServerError>;

impl ServerError {
    pub fn new(status: StatusCode, code: &'static str, message: impl Into<String>) -> Self {
        Self {
            status,
            code,
            message: message.into(),
            source: None,
        }
    }

    pub fn not_found() -> Self {
        Self::new(StatusCode::NOT_FOUND, "not_found", "resource not found")
    }

    pub fn not_implemented() -> Self {
        Self::new(
            StatusCode::NOT_IMPLEMENTED,
            "not_implemented",
            "not implemented",
        )
    }

    fn internal(source: anyhow::Error) -> Self {
        Self {
            status: StatusCode::INTERNAL_SERVER_ERROR,
            code: "internal",
            message: "internal server error".to_owned(),
            source: Some(source),
        }
    }
}

impl<E: HttpError> From<E> for ServerError {
    fn from(err: E) -> Self {
        let status = err.status();
        if status.is_server_error() {
            return Self {
                status,
                code: err.code(),
                ..Self::internal(err.into())
            };
        }

        Self::new(status, err.code(), err.to_string())
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
            tracing::error!(code = self.code, "request failed: {source:#}");
        }

        (
            self.status,
            axum::Json(json!({"code": self.code, "message": self.message})),
        )
            .into_response()
    }
}

impl HttpError for JsonRejection {
    fn status(&self) -> StatusCode {
        match self {
            // Malformed or mistyped JSON is a bad request like any other (§15); a missing content
            // type or an oversized body keeps its own HTTP status.
            Self::JsonDataError(_) | Self::JsonSyntaxError(_) => StatusCode::BAD_REQUEST,
            _ => JsonRejection::status(self),
        }
    }

    fn code(&self) -> &'static str {
        "invalid_request"
    }
}

impl HttpError for OpaqueError {
    fn status(&self) -> StatusCode {
        match self {
            Self::InvalidLogin => StatusCode::UNAUTHORIZED,
            Self::Malformed => StatusCode::BAD_REQUEST,
            // `ReflectedValue` and `EmptyPassword` are only raised by the client half of the
            // protocol; seeing one here means server code called a client function.
            Self::ReflectedValue | Self::EmptyPassword | Self::Protocol(_) => {
                StatusCode::INTERNAL_SERVER_ERROR
            }
        }
    }

    fn code(&self) -> &'static str {
        match self {
            // One code for wrong password and unknown account, so a username's existence never
            // leaks.
            Self::InvalidLogin => "invalid_credentials",
            Self::Malformed => "invalid_request",
            Self::ReflectedValue | Self::EmptyPassword | Self::Protocol(_) => "internal",
        }
    }
}

impl HttpError for sqlx::Error {}

impl HttpError for registration::StartError {
    fn status(&self) -> StatusCode {
        match self {
            Self::InvalidUserId => StatusCode::BAD_REQUEST,
            Self::UsernameTaken | Self::UserIdTaken => StatusCode::CONFLICT,
            Self::Opaque(err) => err.status(),
            Self::Db(_) => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }

    fn code(&self) -> &'static str {
        match self {
            Self::InvalidUserId => "invalid_request",
            Self::UsernameTaken => "username_taken",
            Self::UserIdTaken => "user_id_taken",
            Self::Opaque(err) => err.code(),
            Self::Db(_) => "internal",
        }
    }
}

impl HttpError for registration::FinishError {
    fn status(&self) -> StatusCode {
        match self {
            Self::Expired => StatusCode::BAD_REQUEST,
            Self::Db(_) => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }

    fn code(&self) -> &'static str {
        match self {
            Self::Expired => "registration_expired",
            Self::Db(_) => "internal",
        }
    }
}

impl HttpError for login::StartError {
    fn status(&self) -> StatusCode {
        match self {
            Self::Opaque(err) => err.status(),
            Self::Db(_) => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }

    fn code(&self) -> &'static str {
        match self {
            Self::Opaque(err) => err.code(),
            Self::Db(_) => "internal",
        }
    }
}

impl HttpError for UsernameError {
    fn status(&self) -> StatusCode {
        StatusCode::BAD_REQUEST
    }

    fn code(&self) -> &'static str {
        "invalid_username"
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use http_body_util::BodyExt;
    use serde_json::{Value, json};

    async fn render(err: impl Into<ServerError>) -> (StatusCode, Value) {
        let response = err.into().into_response();
        let status = response.status();
        let body = response.into_body().collect().await.unwrap().to_bytes();
        (status, serde_json::from_slice(&body).unwrap())
    }

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
    async fn default_impl_is_internal() {
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
