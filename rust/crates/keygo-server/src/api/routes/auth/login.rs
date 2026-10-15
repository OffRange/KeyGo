use crate::api::{AppState, Json, ServerResult};
use crate::domain::{LoginId, Username};
use crate::service::login::{self, FinishLogin, StartLogin};
use axum::Router;
use axum::extract::State;
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
struct LoginStartRequest {
    username: String,
    credential_request: CredentialRequest,
}

#[derive(Deserialize)]
struct LoginFinishRequest {
    login_id: LoginId,
    credential_finalization: CredentialFinalization,
}

#[derive(Serialize)]
struct LoginStart {
    login_id: LoginId,
    credential_response: CredentialResponse,
    expires_in: u64,
}

#[derive(Serialize)]
struct LoginFinish {
    user_id: Uuid,
    username: Username,
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

async fn finish(
    State(state): State<AppState>,
    Json(payload): Json<LoginFinishRequest>,
) -> ServerResult<Json<LoginFinish>> {
    let input = FinishLogin {
        login_id: payload.login_id,
        credential_finalization: payload.credential_finalization,
    };

    let finished = login::finish(&state.db, input).await?;

    Ok(Json(LoginFinish {
        user_id: finished.user_id,
        username: finished.username,
    }))
}
