use crate::api::authenticated::Authenticated;
use crate::api::routes::auth::handshake::StartResponse;
use crate::api::{AppState, Json, ServerResult};
use crate::service::step_up::{self, StartStepUp};
use axum::Router;
use axum::extract::State;
use axum::routing::post;
use keygo_opaque::CredentialRequest;
use serde::Deserialize;

pub fn router() -> Router<AppState> {
    Router::new().route("/start", post(start))
}

#[derive(Deserialize)]
struct StartRequest {
    credential_request: CredentialRequest,
}

async fn start(
    State(state): State<AppState>,
    authenticated: Authenticated,
    Json(request): Json<StartRequest>,
) -> ServerResult<Json<StartResponse>> {
    let input = StartStepUp {
        user_id: authenticated.claims().user_id(),
        credential_request: request.credential_request,
    };

    let started =
        step_up::start(&state.db, &state.step_up_states, &state.opaque_state, input).await?;

    Ok(Json(started.into()))
}

#[cfg(test)]
mod tests {
    use crate::testing::{
        PASSWORD, app_state, device_json, post_to, post_with_proof, post_with_token, prover,
        signed_in,
    };
    use axum::http::StatusCode;
    use keygo_opaque::CredentialResponse;
    use keygo_opaque::client::ClientLogin;
    use keygo_opaque::server::ServerSetup;
    use serde_json::{Value, json};
    use sqlx::PgPool;

    const START: &str = "/api/v1/auth/step-up/start";

    fn start_body() -> (ClientLogin, Value) {
        let (client, request) = ClientLogin::start(PASSWORD).unwrap();
        (client, json!({ "credential_request": request.to_base64() }))
    }

    #[sqlx::test]
    async fn starts_a_step_up_over_http(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;
        let (client, body) = start_body();

        let (status, started) =
            post_with_token(&state, &prover, START, issued.access_token.expose(), body).await;

        assert_eq!(status, StatusCode::OK);
        assert_eq!(started["expires_in"], state.step_up_states.ttl().as_secs());
        let response =
            CredentialResponse::from_base64(started["credential_response"].as_str().unwrap())
                .unwrap();
        assert!(client.finish(PASSWORD, response).is_ok());
        assert_eq!(state.step_up_states.count(), 1);
        assert_eq!(state.login_states.count(), 0);
    }

    #[sqlx::test]
    async fn start_requires_an_access_token(db: PgPool) {
        let (_, body) = start_body();

        let (status, response) =
            post_to(app_state(&db, &ServerSetup::generate()), START, body).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(response["code"], "invalid_token");
    }

    #[sqlx::test]
    async fn a_step_up_cannot_be_finished_as_a_sign_in(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;
        let (client, body) = start_body();
        let (_, started) =
            post_with_token(&state, &prover, START, issued.access_token.expose(), body).await;
        let response =
            CredentialResponse::from_base64(started["credential_response"].as_str().unwrap())
                .unwrap();
        let finish = client.finish(PASSWORD, response).unwrap();
        let body = json!({
            "login_id": started["login_id"],
            "credential_finalization": finish.finalization.to_base64(),
            "dpop_binding": finish.session_key.dpop_binding(prover.jkt()).to_base64(),
            "device": device_json(),
        });

        let (status, response) =
            post_with_proof(&state, &prover, "/api/v1/auth/login/finish", body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(response["code"], "login_expired");
        assert_eq!(state.step_up_states.count(), 1);
    }
}
