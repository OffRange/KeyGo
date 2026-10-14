use crate::api::AppState;
use axum::extract::State;
use axum::http::StatusCode;
use axum::routing::post;
use axum::{Json, Router};
use keygo_opaque::{
    OpaqueError, RegistrationRequest, RegistrationResponse, RegistrationUpload, server,
};
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
) -> Result<Json<RegisterStart>, StatusCode> {
    if payload.username.is_empty() {
        return Err(StatusCode::UNPROCESSABLE_ENTITY);
    }

    let response = server::registration_start(
        &state.opaque_state,
        payload.registration_request,
        payload.user_id.as_bytes(),
    )
    .map_err(opaque_status)?;

    Ok(Json(RegisterStart {
        registration_response: response,
    }))
}

async fn finish(
    State(state): State<AppState>,
    Json(payload): Json<RegisterFinishRequest>,
) -> StatusCode {
    StatusCode::NOT_IMPLEMENTED
}

fn opaque_status(err: OpaqueError) -> StatusCode {
    match err {
        OpaqueError::Malformed => StatusCode::UNPROCESSABLE_ENTITY,
        other => {
            tracing::error!("OPAQUE registration failed: {other}");
            StatusCode::INTERNAL_SERVER_ERROR
        }
    }
}
