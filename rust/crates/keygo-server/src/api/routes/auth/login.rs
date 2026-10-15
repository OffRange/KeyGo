use crate::api::{AppState, ErrorCode, HttpError, Json, ServerResult};
use crate::domain::{LoginId, Username};
use crate::service::login::{self, FinishLogin, StartLogin};
use axum::Router;
use axum::extract::State;
use axum::http::StatusCode;
use axum::routing::post;
use keygo_opaque::{CredentialFinalization, CredentialRequest, CredentialResponse};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/start", post(start))
        .route("/finish", post(finish))
}

#[derive(Deserialize)]
struct StartRequest {
    /// Parsed in the handler so a bad name answers `invalid_username`, not `invalid_request`.
    username: String,
    credential_request: CredentialRequest,
}

#[derive(Serialize)]
struct StartResponse {
    login_id: LoginId,
    credential_response: CredentialResponse,
    expires_in: u64,
}

impl HttpError for login::StartError {
    fn status_and_code(&self) -> (StatusCode, ErrorCode) {
        match self {
            Self::Opaque(err) => err.status_and_code(),
            Self::Db(err) => err.status_and_code(),
        }
    }
}

async fn start(
    State(state): State<AppState>,
    Json(request): Json<StartRequest>,
) -> ServerResult<Json<StartResponse>> {
    let input = StartLogin {
        username: Username::parse(&request.username)?,
        credential_request: request.credential_request,
    };

    let started = login::start(&state.db, &state.opaque_state, input).await?;

    Ok(Json(StartResponse {
        login_id: started.login_id,
        credential_response: started.credential_response,
        expires_in: started.expires_in.as_secs(),
    }))
}

#[derive(Deserialize)]
struct FinishRequest {
    login_id: LoginId,
    credential_finalization: CredentialFinalization,
}

#[derive(Serialize)]
struct FinishResponse {
    user_id: Uuid,
    username: Username,
}

impl HttpError for login::FinishError {
    fn status_and_code(&self) -> (StatusCode, ErrorCode) {
        match self {
            Self::Expired => (StatusCode::BAD_REQUEST, ErrorCode::LoginExpired),
            Self::Opaque(err) => err.status_and_code(),
            Self::Db(err) => err.status_and_code(),
        }
    }
}

async fn finish(
    State(state): State<AppState>,
    Json(request): Json<FinishRequest>,
) -> ServerResult<Json<FinishResponse>> {
    let input = FinishLogin {
        login_id: request.login_id,
        credential_finalization: request.credential_finalization,
    };

    let finished = login::finish(&state.db, input).await?;

    Ok(Json(FinishResponse {
        user_id: finished.user_id,
        username: finished.username,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::api::error::render;
    use keygo_opaque::OpaqueError;

    #[tokio::test]
    async fn expired_logins_are_bad_requests() {
        let (status, body) = render(login::FinishError::Expired).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "login_expired");
    }

    #[tokio::test]
    async fn failed_login_finish_matches_failed_start() {
        let (status, body) = render(login::FinishError::Opaque(OpaqueError::InvalidLogin)).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_credentials");
    }
}
