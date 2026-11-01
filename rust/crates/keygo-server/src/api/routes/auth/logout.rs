use crate::api::authenticated::Authenticated;
use crate::api::{AppState, ServerResult};
use crate::service::session;
use axum::Router;
use axum::extract::State;
use axum::http::StatusCode;
use axum::routing::post;

pub fn router() -> Router<AppState> {
    Router::new().route("/", post(logout))
}

async fn logout(
    State(state): State<AppState>,
    authenticated: Authenticated,
) -> ServerResult<StatusCode> {
    session::logout(&state.db, authenticated.claims().session_id()).await?;
    Ok(StatusCode::NO_CONTENT)
}

#[cfg(test)]
mod tests {
    use crate::api::AppState;
    use crate::domain::RefreshToken;
    use crate::testing::{
        app_state, post_with_proof, prover, send_with_nonce, signed_in, with_proof,
    };
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use keygo_dpop::DpopProver;
    use keygo_dpop::testing::TestSigner;
    use keygo_opaque::server::ServerSetup;
    use serde_json::{Value, json};
    use sqlx::PgPool;

    const LOGOUT: &str = "/api/v1/auth/logout";

    async fn logout(
        state: &AppState,
        prover: &DpopProver<TestSigner>,
        access_token: &str,
    ) -> (StatusCode, Value) {
        send_with_nonce(state, |nonce| {
            let request = Request::post(LOGOUT).body(Body::empty()).unwrap();
            with_proof(state, prover, request, Some(access_token), nonce)
        })
        .await
    }

    fn token_body(refresh_token: &RefreshToken) -> Value {
        json!({ "refresh_token": refresh_token.expose_encoded().as_str() })
    }

    #[sqlx::test]
    async fn logout_answers_no_content(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;

        let (status, body) = logout(&state, &prover, issued.access_token.expose()).await;

        assert_eq!(status, StatusCode::NO_CONTENT);
        assert_eq!(body, Value::Null);
    }

    /// Repeating it changes nothing on the server, but the access token no longer authenticates.
    #[sqlx::test]
    async fn access_token_is_revoked_for_logout(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;
        logout(&state, &prover, issued.access_token.expose()).await;

        let (status, body) = logout(&state, &prover, issued.access_token.expose()).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "session_revoked");
        assert_eq!(body["reason"], "logout");
    }

    #[sqlx::test]
    async fn refresh_token_is_revoked_for_logout(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;
        logout(&state, &prover, issued.access_token.expose()).await;

        let (status, body) = post_with_proof(
            &state,
            &prover,
            "/api/v1/auth/token",
            token_body(&issued.refresh_token),
        )
        .await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "session_revoked");
        assert_eq!(body["reason"], "logout");
    }

    #[sqlx::test]
    async fn logout_requires_an_access_token(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());

        let (status, body) = post_with_proof(&state, &prover(), LOGOUT, Value::Null).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_token");
    }
}
