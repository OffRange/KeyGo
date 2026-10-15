use crate::api::{AppState, Json, ServerError, ServerResult};
use crate::domain::Username;
use axum::Router;
use axum::extract::State;
use axum::routing::post;
use keygo_opaque::{RegistrationRequest, RegistrationResponse, RegistrationUpload, server};
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
    registration_upload: RegistrationUpload,
}

#[derive(Serialize)]
struct RegisterStart {
    registration_response: RegistrationResponse,
}

async fn start(
    State(state): State<AppState>,
    Json(payload): Json<RegisterStartRequest>,
) -> ServerResult<Json<RegisterStart>> {
    let username = Username::parse(&payload.username)?;

    let response = server::registration_start(
        &state.opaque_state,
        payload.registration_request,
        payload.user_id.as_bytes(),
    )?;

    Ok(Json(RegisterStart {
        registration_response: response,
    }))
}

async fn finish(
    State(state): State<AppState>,
    Json(payload): Json<RegisterFinishRequest>,
) -> ServerResult<()> {
    Err(ServerError::not_implemented())
}
