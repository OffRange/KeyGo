use crate::api::{AppState, Json, ServerResult};
use crate::domain::{RegistrationId, Username};
use crate::service::registration::{self, FinishRegistration, StartRegistration};
use axum::Router;
use axum::extract::State;
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
struct RegisterStartRequest {
    username: String,
    user_id: Uuid,
    invite: Option<String>,
    registration_request: RegistrationRequest,
}

#[derive(Deserialize)]
struct RegisterFinishRequest {
    registration_id: RegistrationId,
    registration_upload: RegistrationUpload,
}

#[derive(Serialize)]
struct RegisterStart {
    registration_id: RegistrationId,
    registration_response: RegistrationResponse,
    expires_in: u64,
}

#[derive(Serialize)]
struct RegisterFinish {
    pub username: Username,
}

async fn start(
    State(state): State<AppState>,
    Json(payload): Json<RegisterStartRequest>,
) -> ServerResult<Json<RegisterStart>> {
    let input = StartRegistration {
        username: Username::parse(&payload.username)?,
        user_id: payload.user_id,
        request: payload.registration_request,
    };

    let started = registration::start(&state.db, &state.opaque_state, input).await?;

    Ok(Json(RegisterStart {
        registration_id: started.registration_id,
        registration_response: started.response,
        expires_in: started.expires_in.as_secs(),
    }))
}

async fn finish(
    State(state): State<AppState>,
    Json(payload): Json<RegisterFinishRequest>,
) -> ServerResult<Json<RegisterFinish>> {
    let input = FinishRegistration {
        registration_id: payload.registration_id,
        registration_upload: payload.registration_upload,
    };

    let finished = registration::finish(&state.db, input).await?;

    Ok(Json(RegisterFinish {
        username: finished.username,
    }))
}
