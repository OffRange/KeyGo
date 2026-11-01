use crate::api::authenticated::{AuthError, Authenticated};
use crate::api::routes::auth::handshake::StartResponse;
use crate::api::routes::auth::session::Access;
use crate::api::{AppState, ErrorCode, HttpError, Json, ServerResult};
use crate::domain::LoginId;
use crate::service::step_up::{self, FinishError, FinishStepUp, StartStepUp};
use axum::Router;
use axum::extract::State;
use axum::http::HeaderMap;
use axum::routing::post;
use keygo_opaque::{CredentialFinalization, CredentialRequest, DpopBinding};
use serde::{Deserialize, Serialize};

impl HttpError for FinishError {
    fn code(&self) -> ErrorCode {
        match self {
            Self::Handshake(err) => err.code(),
            Self::SessionEnded => AuthError::Revoked(None).code(),
        }
    }

    fn headers(&self) -> HeaderMap {
        match self {
            Self::Handshake(err) => err.headers(),
            Self::SessionEnded => AuthError::Revoked(None).headers(),
        }
    }
}

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/start", post(start))
        .route("/finish", post(finish))
}

#[derive(Deserialize)]
struct StartRequest {
    credential_request: CredentialRequest,
}

#[derive(Deserialize)]
struct FinishRequest {
    login_id: LoginId,
    credential_finalization: CredentialFinalization,
    dpop_binding: DpopBinding,
}

#[derive(Serialize)]
struct FinishResponse {
    #[serde(flatten)]
    access: Access,
    auth_time: u64,
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

async fn finish(
    State(state): State<AppState>,
    authenticated: Authenticated,
    Json(request): Json<FinishRequest>,
) -> ServerResult<Json<FinishResponse>> {
    let input = FinishStepUp {
        session_id: authenticated.claims().session_id(),
        user_id: authenticated.claims().user_id(),
        login_id: request.login_id,
        credential_finalization: request.credential_finalization,
        binding: request.dpop_binding,
        jkt: *authenticated.claims().jkt(),
    };

    let finished =
        step_up::finish(&state.db, &state.step_up_states, &state.token_issuer, input).await?;

    Ok(Json(FinishResponse {
        access: finished.access_token.into(),
        auth_time: finished.auth_time,
    }))
}

#[cfg(test)]
mod tests {
    use crate::api::AppState;
    use crate::api::error::render;
    use crate::db::devices;
    use crate::domain::{
        ACCESS_TOKEN_LIFETIME, AccessToken, DeviceId, FRESH_AUTH_WINDOW, SessionId, unix_now,
    };
    use crate::service::step_up::FinishError;
    use crate::testing::{
        PASSWORD, app_state, count, device_info, device_json, issue_session, post_to,
        post_with_proof, post_with_token, prover, signed_in,
    };
    use axum::http::StatusCode;
    use keygo_dpop::DpopProver;
    use keygo_dpop::testing::TestSigner;
    use keygo_opaque::CredentialResponse;
    use keygo_opaque::client::ClientLogin;
    use keygo_opaque::server::ServerSetup;
    use serde_json::{Value, json};
    use sqlx::PgPool;

    const START: &str = "/api/v1/auth/step-up/start";
    const FINISH: &str = "/api/v1/auth/step-up/finish";

    fn start_body() -> (ClientLogin, Value) {
        let (client, request) = ClientLogin::start(PASSWORD).unwrap();
        (client, json!({ "credential_request": request.to_base64() }))
    }

    async fn start_over_http(
        state: &AppState,
        prover: &DpopProver<TestSigner>,
        access_token: &str,
    ) -> (ClientLogin, Value) {
        let (client, body) = start_body();
        let (status, started) = post_with_token(state, prover, START, access_token, body).await;
        assert_eq!(status, StatusCode::OK);
        (client, started)
    }

    fn finish_body(client: ClientLogin, started: &Value, jkt: &[u8; 32]) -> Value {
        let response =
            CredentialResponse::from_base64(started["credential_response"].as_str().unwrap())
                .unwrap();
        let finish = client.finish(PASSWORD, response).unwrap();
        json!({
            "login_id": started["login_id"],
            "credential_finalization": finish.finalization.to_base64(),
            "dpop_binding": finish.session_key.dpop_binding(jkt).to_base64(),
        })
    }

    struct Stale {
        session_id: SessionId,
        auth_time: u64,
        access_token: String,
    }

    /// Moves the session behind `access_token` past the step-up window, row and token alike.
    async fn go_stale(state: &AppState, access_token: &AccessToken) -> Stale {
        let claims = state.token_issuer.verify(access_token.expose()).unwrap();
        let auth_time = unix_now() - FRESH_AUTH_WINDOW.as_secs() - 1;
        sqlx::query("update sessions set auth_time = to_timestamp($1) where id = $2")
            .bind(auth_time as i64)
            .bind(claims.session_id().as_uuid())
            .execute(&state.db)
            .await
            .unwrap();
        let token = state.token_issuer.issue(
            claims.user_id(),
            claims.session_id(),
            auth_time,
            claims.jkt(),
        );
        Stale {
            session_id: claims.session_id(),
            auth_time,
            access_token: token.expose().to_owned(),
        }
    }

    async fn auth_time_of(db: &PgPool, session_id: SessionId) -> u64 {
        let auth_time: i64 = sqlx::query_scalar(
            "select extract(epoch from auth_time)::int8 from sessions where id = $1",
        )
        .bind(session_id.as_uuid())
        .fetch_one(db)
        .await
        .unwrap();
        auth_time as u64
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

    #[sqlx::test]
    async fn finish_makes_a_stale_session_fresh(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;
        let stale = go_stale(&state, &issued.access_token).await;
        let (client, started) = start_over_http(&state, &prover, &stale.access_token).await;
        let body = finish_body(client, &started, prover.jkt());

        let (status, finished) =
            post_with_token(&state, &prover, FINISH, &stale.access_token, body).await;

        assert_eq!(status, StatusCode::OK);
        let claims = state
            .token_issuer
            .verify(finished["access_token"].as_str().unwrap())
            .unwrap();
        assert!(claims.is_fresh());
        assert_eq!(claims.session_id(), stale.session_id);
        assert_eq!(claims.jkt(), prover.jkt());
        assert_eq!(finished["auth_time"], claims.auth_time());
        assert_eq!(
            finished["access_expires_in"],
            ACCESS_TOKEN_LIFETIME.as_secs()
        );
        assert_eq!(
            auth_time_of(&db, stale.session_id).await,
            claims.auth_time()
        );
        assert_eq!(state.step_up_states.count(), 0);
    }

    #[sqlx::test]
    async fn finish_keeps_the_session_and_its_refresh_token(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;
        let stale = go_stale(&state, &issued.access_token).await;
        let (client, started) = start_over_http(&state, &prover, &stale.access_token).await;
        let body = finish_body(client, &started, prover.jkt());
        let sessions = count(&db, "sessions").await;

        let (_, finished) =
            post_with_token(&state, &prover, FINISH, &stale.access_token, body).await;

        assert!(finished.get("refresh_token").is_none());
        assert_eq!(count(&db, "sessions").await, sessions);
        let rotated: bool = sqlx::query_scalar(
            "select rotated_at is not null from refresh_tokens where session_id = $1",
        )
        .bind(stale.session_id.as_uuid())
        .fetch_one(&db)
        .await
        .unwrap();
        assert!(!rotated);
    }

    #[sqlx::test]
    async fn binding_for_another_key_is_unauthorized(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;
        let stale = go_stale(&state, &issued.access_token).await;
        let (client, started) = start_over_http(&state, &prover, &stale.access_token).await;
        let body = finish_body(client, &started, &[0x41; 32]);

        let (status, body) =
            post_with_token(&state, &prover, FINISH, &stale.access_token, body).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_credentials");
        assert_eq!(auth_time_of(&db, stale.session_id).await, stale.auth_time);
        assert_eq!(state.step_up_states.count(), 0);
    }

    #[sqlx::test]
    async fn a_relayed_step_up_does_not_freshen_another_session(db: PgPool) {
        let victim = prover();
        let (state, issued) = signed_in(&db, &victim).await;
        let user_id = state
            .token_issuer
            .verify(issued.access_token.expose())
            .unwrap()
            .user_id();
        let attacker = DpopProver::new(TestSigner::from_seed(9));
        let device_id = DeviceId::random();
        let mut conn = db.acquire().await.unwrap();
        devices::insert(&mut conn, device_id, user_id, &device_info())
            .await
            .unwrap();
        let attacker_session = issue_session(&db, user_id, device_id, attacker.jkt()).await;
        let stale = go_stale(&state, &attacker_session.access_token).await;
        // The victim's client binds the run to its own key; the attacker forwards it unchanged.
        let (client, started) = start_over_http(&state, &attacker, &stale.access_token).await;
        let body = finish_body(client, &started, victim.jkt());

        let (status, body) =
            post_with_token(&state, &attacker, FINISH, &stale.access_token, body).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_credentials");
        assert_eq!(auth_time_of(&db, stale.session_id).await, stale.auth_time);
    }

    #[sqlx::test]
    async fn replayed_finish_is_expired(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;
        let token = issued.access_token.expose();
        let (client, started) = start_over_http(&state, &prover, token).await;
        let body = finish_body(client, &started, prover.jkt());

        let (accepted, _) = post_with_token(&state, &prover, FINISH, token, body.clone()).await;
        let (status, body) = post_with_token(&state, &prover, FINISH, token, body).await;

        assert_eq!(accepted, StatusCode::OK);
        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "login_expired");
    }

    #[sqlx::test]
    async fn finish_requires_an_access_token(db: PgPool) {
        let (status, response) =
            post_to(app_state(&db, &ServerSetup::generate()), FINISH, json!({})).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(response["code"], "invalid_token");
    }

    #[tokio::test]
    async fn a_session_ended_meanwhile_answers_like_a_revoked_one() {
        let (status, body) = render(FinishError::SessionEnded).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "session_revoked");
        assert!(body.get("reason").is_none());
    }
}
