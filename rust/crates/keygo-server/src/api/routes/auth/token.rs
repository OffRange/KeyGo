use crate::api::dpop::SingleUseDpopProof;
use crate::api::routes::auth::session::Session;
use crate::api::{AppState, ErrorCode, HttpError, Json, ServerResult};
use crate::domain::{RefreshToken, RevokeReason};
use crate::service::session;
use crate::service::session::RefreshError;
use axum::extract::State;
use serde::Deserialize;

#[derive(Deserialize)]
pub struct TokenRequest {
    refresh_token: RefreshToken,
}

impl HttpError for RefreshError {
    fn code(&self) -> ErrorCode {
        match self {
            Self::Revoked(_) => ErrorCode::SessionRevoked,
            Self::Expired => ErrorCode::RefreshExpired,
            Self::WrongKey => ErrorCode::InvalidDpopProof,
            Self::Db(_) => ErrorCode::Internal,
        }
    }

    fn reason(&self) -> Option<&'static str> {
        match self {
            Self::Revoked(reason) => reason.map(RevokeReason::as_str),
            _ => None,
        }
    }
}

pub async fn token(
    State(state): State<AppState>,
    proof: SingleUseDpopProof,
    Json(request): Json<TokenRequest>,
) -> ServerResult<Json<Session>> {
    let issued = session::refresh(
        &state.db,
        &state.token_issuer,
        &request.refresh_token,
        &proof.jkt,
    )
    .await?;

    Ok(Json(issued.into()))
}

#[cfg(test)]
mod tests {
    use crate::testing::{
        PASSWORD, app_state, device_json, post_to, post_with_proof, prover, register,
        replayable_post, send,
    };
    use axum::http::StatusCode;
    use axum::http::header::CACHE_CONTROL;
    use keygo_dpop::DpopProver;
    use keygo_dpop::testing::TestSigner;
    use keygo_opaque::CredentialResponse;
    use keygo_opaque::client::ClientLogin;
    use keygo_opaque::server::ServerSetup;
    use serde_json::{Value, json};
    use sqlx::PgPool;

    const TOKEN: &str = "/api/v1/auth/token";

    /// Signs in over HTTP and returns the session object.
    async fn sign_in(state: &crate::api::AppState, prover: &DpopProver<TestSigner>) -> Value {
        let (client, request) = ClientLogin::start(PASSWORD).unwrap();
        let body = json!({ "username": "ada", "credential_request": request.to_base64() });
        let (_, started) = post_to(state.clone(), "/api/v1/auth/login/start", body).await;
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
        let (status, finished) =
            post_with_proof(state, prover, "/api/v1/auth/login/finish", body).await;
        assert_eq!(status, StatusCode::OK);
        finished["session"].clone()
    }

    fn token_body(session: &Value) -> Value {
        json!({ "refresh_token": session["refresh_token"] })
    }

    #[sqlx::test]
    async fn refreshes_over_http(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;
        let prover = prover();
        let session = sign_in(&state, &prover).await;

        let (status, refreshed) =
            post_with_proof(&state, &prover, TOKEN, token_body(&session)).await;

        assert_eq!(status, StatusCode::OK);
        assert_eq!(refreshed["device_id"], session["device_id"]);
        assert_ne!(refreshed["refresh_token"], session["refresh_token"]);
    }

    #[sqlx::test]
    async fn issued_tokens_are_not_stored(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;
        let prover = prover();
        let session = sign_in(&state, &prover).await;
        let request = replayable_post(&state, &prover, TOKEN, &token_body(&session)).await;

        let (status, headers, _) = send(&state, request()).await;

        assert_eq!(status, StatusCode::OK);
        assert_eq!(headers[CACHE_CONTROL], "no-store");
    }

    #[sqlx::test]
    async fn reuse_answers_session_revoked_with_its_reason(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;
        let prover = prover();
        let session = sign_in(&state, &prover).await;
        post_with_proof(&state, &prover, TOKEN, token_body(&session)).await;

        let (status, body) = post_with_proof(&state, &prover, TOKEN, token_body(&session)).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "session_revoked");
        assert_eq!(body["reason"], "reuse_detected");
    }

    /// The endpoint skips the jti check: the rotated token already turns a replay away, and as a
    /// reuse it revokes the session.
    #[sqlx::test]
    async fn replayed_refresh_revokes_the_session(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;
        let prover = prover();
        let session = sign_in(&state, &prover).await;
        let replay = replayable_post(&state, &prover, TOKEN, &token_body(&session)).await;

        let (accepted, _, refreshed) = send(&state, replay()).await;
        let (status, _, body) = send(&state, replay()).await;

        assert_eq!(accepted, StatusCode::OK);
        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "session_revoked");
        assert_eq!(body["reason"], "reuse_detected");
        let (status, body) = post_with_proof(&state, &prover, TOKEN, token_body(&refreshed)).await;
        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "session_revoked");
    }

    #[sqlx::test]
    async fn another_key_is_an_invalid_proof(db: PgPool) {
        let setup = ServerSetup::generate();
        let state = app_state(&db, &setup);
        register(&db, &setup, "ada").await;
        let session = sign_in(&state, &prover()).await;
        let other = DpopProver::new(TestSigner::from_seed(8));

        let (status, body) = post_with_proof(&state, &other, TOKEN, token_body(&session)).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_dpop_proof");
    }

    #[sqlx::test]
    async fn unknown_token_is_revoked_without_a_reason(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let token = crate::domain::RefreshToken::generate();
        let body = json!({ "refresh_token": token.expose_encoded().as_str() });

        let (status, body) = post_with_proof(&state, &prover(), TOKEN, body).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "session_revoked");
        assert!(body.get("reason").is_none());
    }

    #[sqlx::test]
    async fn malformed_token_is_an_invalid_request(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let body = json!({ "refresh_token": "kgr_short" });

        let (status, body) = post_with_proof(&state, &prover(), TOKEN, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_request");
    }
}
