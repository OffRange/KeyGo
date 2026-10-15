use crate::api::{AppState, ErrorCode, HttpError, Json, ServerResult};
use crate::domain::{RegistrationId, Username};
use crate::service::registration::{self, FinishRegistration, StartRegistration};
use axum::Router;
use axum::extract::State;
use axum::http::StatusCode;
use axum::routing::post;
use keygo_opaque::{RegistrationRequest, RegistrationResponse, RegistrationUpload};
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
    user_id: Uuid,
    invite: Option<String>,
    registration_request: RegistrationRequest,
}

#[derive(Serialize)]
struct StartResponse {
    registration_id: RegistrationId,
    registration_response: RegistrationResponse,
    expires_in: u64,
}

impl HttpError for registration::StartError {
    fn status_and_code(&self) -> (StatusCode, ErrorCode) {
        match self {
            Self::InvalidUserId => (StatusCode::BAD_REQUEST, ErrorCode::InvalidRequest),
            Self::UsernameTaken => (StatusCode::CONFLICT, ErrorCode::UsernameTaken),
            Self::UserIdTaken => (StatusCode::CONFLICT, ErrorCode::UserIdTaken),
            Self::Opaque(err) => err.status_and_code(),
            Self::Db(err) => err.status_and_code(),
        }
    }
}

async fn start(
    State(state): State<AppState>,
    Json(request): Json<StartRequest>,
) -> ServerResult<Json<StartResponse>> {
    let input = StartRegistration {
        username: Username::parse(&request.username)?,
        user_id: request.user_id,
        registration_request: request.registration_request,
    };

    let started = registration::start(&state.db, &state.opaque_state, input).await?;

    Ok(Json(StartResponse {
        registration_id: started.registration_id,
        registration_response: started.registration_response,
        expires_in: started.expires_in.as_secs(),
    }))
}

#[derive(Deserialize)]
struct FinishRequest {
    registration_id: RegistrationId,
    registration_upload: RegistrationUpload,
}

#[derive(Serialize)]
struct FinishResponse {
    username: Username,
}

impl HttpError for registration::FinishError {
    fn status_and_code(&self) -> (StatusCode, ErrorCode) {
        match self {
            Self::Expired => (StatusCode::BAD_REQUEST, ErrorCode::RegistrationExpired),
            Self::Db(err) => err.status_and_code(),
        }
    }
}

async fn finish(
    State(state): State<AppState>,
    Json(request): Json<FinishRequest>,
) -> ServerResult<Json<FinishResponse>> {
    let input = FinishRegistration {
        registration_id: request.registration_id,
        registration_upload: request.registration_upload,
    };

    let finished = registration::finish(&state.db, input).await?;

    Ok(Json(FinishResponse {
        username: finished.username,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::api::error::render;

    #[tokio::test]
    async fn expired_registrations_are_bad_requests() {
        let (status, body) = render(registration::FinishError::Expired).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "registration_expired");
    }

    #[tokio::test]
    async fn taken_usernames_conflict() {
        let (status, body) = render(registration::StartError::UsernameTaken).await;

        assert_eq!(status, StatusCode::CONFLICT);
        assert_eq!(body["code"], "username_taken");
    }
}
