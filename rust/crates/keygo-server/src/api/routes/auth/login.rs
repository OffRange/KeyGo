use crate::api::{AppState, Json, ServerError, ServerResult};
use crate::domain::{LoginId, Username};
use crate::service::login::{self, StartLogin};
use axum::Router;
use axum::extract::State;
use axum::routing::post;
use keygo_opaque::{CredentialRequest, CredentialResponse};
use serde::{Deserialize, Serialize};

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/start", post(start))
        .route("/finish", post(finish))
}

#[derive(Deserialize)]
struct LoginStartRequest {
    username: String,
    credential_request: CredentialRequest,
}

#[derive(Serialize)]
struct LoginStart {
    login_id: LoginId,
    credential_response: CredentialResponse,
    expires_in: u64,
}

async fn start(
    State(state): State<AppState>,
    Json(payload): Json<LoginStartRequest>,
) -> ServerResult<Json<LoginStart>> {
    let input = StartLogin {
        username: Username::parse(&payload.username)?,
        request: payload.credential_request,
    };

    let started = login::start(&state.db, &state.opaque_state, input).await?;

    Ok(Json(LoginStart {
        login_id: started.login_id,
        credential_response: started.response,
        expires_in: started.expires_in.as_secs(),
    }))
}

async fn finish() -> ServerResult<()> {
    Err(ServerError::not_implemented())
}
