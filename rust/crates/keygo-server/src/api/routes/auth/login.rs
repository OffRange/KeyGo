use crate::api::dpop::DpopProof;
use crate::api::{AppState, ErrorCode, HttpError, Json, ServerResult};
use crate::domain::{LoginId, Username};
use crate::service::login::{self, FinishLogin, StartLogin};
use axum::Router;
use axum::extract::State;
use axum::routing::post;
use keygo_opaque::{CredentialFinalization, CredentialRequest, CredentialResponse, DpopBinding};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/start", post(start))
        .route("/finish", post(finish))
}

impl HttpError for login::Error {
    fn code(&self) -> ErrorCode {
        match self {
            Self::Expired => ErrorCode::LoginExpired,
            Self::Opaque(err) => err.code(),
            Self::Db(_) => ErrorCode::Internal,
        }
    }
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

async fn start(
    State(state): State<AppState>,
    Json(request): Json<StartRequest>,
) -> ServerResult<Json<StartResponse>> {
    let input = StartLogin {
        username: Username::parse(&request.username)?,
        credential_request: request.credential_request,
    };

    let started = login::start(&state.db, &state.login_states, &state.opaque_state, input).await?;

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
    dpop_binding: DpopBinding,
}

#[derive(Serialize)]
struct FinishResponse {
    user_id: Uuid,
    username: Username,
}

async fn finish(
    State(state): State<AppState>,
    proof: DpopProof,
    Json(request): Json<FinishRequest>,
) -> ServerResult<Json<FinishResponse>> {
    let input = FinishLogin {
        login_id: request.login_id,
        credential_finalization: request.credential_finalization,
        binding: request.dpop_binding,
        jkt: proof.jkt,
    };

    let finished = login::finish(&state.db, &state.login_states, input).await?;

    Ok(Json(FinishResponse {
        user_id: finished.user_id,
        username: finished.username,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::api::error::render;
    use crate::testing::{
        PASSWORD, app_state, post_to, post_with_proof, proof_post, prover, register, send,
    };
    use axum::http::StatusCode;
    use keygo_opaque::OpaqueError;
    use keygo_opaque::client::ClientLogin;
    use keygo_opaque::server::ServerSetup;
    use serde_json::{Value, json};
    use sqlx::PgPool;

    const START: &str = "/api/v1/auth/login/start";
    const FINISH: &str = "/api/v1/auth/login/finish";

    async fn start_over_http(state: &AppState, username: &str) -> (ClientLogin, Value) {
        let (client, request) = ClientLogin::start(PASSWORD).unwrap();
        let body = json!({ "username": username, "credential_request": request.to_base64() });
        let (status, started) = post_to(state.clone(), START, body).await;
        assert_eq!(status, StatusCode::OK);
        (client, started)
    }

    fn finish_body(
        login_id: &Value,
        client: ClientLogin,
        started: &Value,
        jkt: &[u8; 32],
    ) -> Value {
        let response =
            CredentialResponse::from_base64(started["credential_response"].as_str().unwrap())
                .unwrap();
        let finish = client.finish(PASSWORD, response).unwrap();
        json!({
            "login_id": login_id,
            "credential_finalization": finish.finalization.to_base64(),
            "dpop_binding": finish.session_key.dpop_binding(jkt).to_base64(),
        })
    }

    #[tokio::test]
    async fn expired_logins_are_bad_requests() {
        let (status, body) = render(login::Error::Expired).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "login_expired");
    }

    #[tokio::test]
    async fn failed_login_finish_matches_failed_start() {
        let (status, body) = render(login::Error::Opaque(OpaqueError::InvalidLogin)).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_credentials");
    }

    #[sqlx::test]
    async fn logs_in_over_http(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        let user_id = register(&db, &setup, "ada").await;
        let (client, started) = start_over_http(&state, "Ada").await;

        assert_eq!(started["expires_in"], login::TTL.as_secs());
        let prover = prover();
        let body = finish_body(&started["login_id"], client, &started, prover.jkt());

        let (status, finished) = post_with_proof(&state, &prover, FINISH, body).await;

        assert_eq!(status, StatusCode::OK);
        assert_eq!(finished, json!({ "user_id": user_id, "username": "ada" }));
        assert_eq!(state.login_states.count(), 0);
    }

    #[sqlx::test]
    async fn finish_without_a_proof_keeps_the_login(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;
        let (client, started) = start_over_http(&state, "ada").await;
        let body = finish_body(&started["login_id"], client, &started, prover().jkt());

        let (status, body) = post_to(state.clone(), FINISH, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_dpop_proof");
        assert_eq!(state.login_states.count(), 1);
    }

    #[sqlx::test]
    async fn nonce_challenge_keeps_the_login(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;
        let (client, started) = start_over_http(&state, "ada").await;
        let prover = prover();
        let body = finish_body(&started["login_id"], client, &started, prover.jkt());

        // A verifier that just started accepts no proof without one of its own nonces.
        let request = proof_post(&state, &prover, FINISH, &body, None);
        let (status, headers, challenge) = send(&state, request).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(challenge["code"], "use_dpop_nonce");
        assert!(headers.contains_key("dpop-nonce"));
        assert_eq!(state.login_states.count(), 1);

        let (status, _) = post_with_proof(&state, &prover, FINISH, body).await;

        assert_eq!(status, StatusCode::OK);
    }

    #[sqlx::test]
    async fn binding_for_another_key_is_unauthorized(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;
        let (client, started) = start_over_http(&state, "ada").await;
        let body = finish_body(&started["login_id"], client, &started, &[0x41; 32]);

        let (status, body) = post_with_proof(&state, &prover(), FINISH, body).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_credentials");
        assert_eq!(state.login_states.count(), 0);
    }

    #[sqlx::test]
    async fn unknown_user_starts_like_a_known_one(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;

        let (_, known) = start_over_http(&state, "ada").await;
        let (_, unknown) = start_over_http(&state, "bob").await;

        let shape = |body: &Value| {
            body.as_object()
                .unwrap()
                .iter()
                .map(|(key, value)| (key.clone(), value.as_str().map(str::len)))
                .collect::<Vec<_>>()
        };
        assert_eq!(shape(&known), shape(&unknown));
    }

    #[sqlx::test]
    async fn start_rejects_an_invalid_username(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let (_, request) = ClientLogin::start(PASSWORD).unwrap();
        let body = json!({ "username": "a", "credential_request": request.to_base64() });

        let (status, body) = post_to(state.clone(), START, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_username");
    }

    #[sqlx::test]
    async fn start_rejects_a_malformed_credential_request(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let body = json!({ "username": "ada", "credential_request": "AAAA" });

        let (status, body) = post_to(state.clone(), START, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_request");
        assert_eq!(state.login_states.count(), 0);
    }

    #[sqlx::test]
    async fn foreign_finalization_is_unauthorized(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;
        let (_, target) = start_over_http(&state, "ada").await;
        let (client, other) = start_over_http(&state, "ada").await;
        let prover = prover();
        let body = finish_body(&target["login_id"], client, &other, prover.jkt());

        let (status, body) = post_with_proof(&state, &prover, FINISH, body).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_credentials");
    }

    #[sqlx::test]
    async fn unknown_user_finish_is_unauthorized(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;
        let (_, fake) = start_over_http(&state, "bob").await;
        let (client, real) = start_over_http(&state, "ada").await;
        let prover = prover();
        let body = finish_body(&fake["login_id"], client, &real, prover.jkt());

        let (status, body) = post_with_proof(&state, &prover, FINISH, body).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_credentials");
    }

    #[sqlx::test]
    async fn finish_rejects_an_unknown_login(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;
        let (client, started) = start_over_http(&state, "ada").await;
        let prover = prover();
        let body = finish_body(&json!(LoginId::random()), client, &started, prover.jkt());

        let (status, body) = post_with_proof(&state, &prover, FINISH, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "login_expired");
    }

    #[sqlx::test]
    async fn finish_rejects_a_malformed_login_id(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let body = json!({
            "login_id": "short",
            "credential_finalization": "AAAA",
            "dpop_binding": "AAAA",
        });

        let (status, body) = post_with_proof(&state, &prover(), FINISH, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_request");
    }
}
