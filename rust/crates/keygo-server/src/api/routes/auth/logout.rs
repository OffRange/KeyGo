use crate::api::authenticated::Authenticated;
use crate::api::{AppState, ServerResult};
use crate::service::session;
use axum::extract::State;
use axum::http::StatusCode;

pub async fn logout(
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
    use crate::service::session::Issued;
    use crate::testing::{app_state, insert_device, issue_session, post_with_proof, prover, send};
    use axum::body::Body;
    use axum::http::header::AUTHORIZATION;
    use axum::http::{Request, StatusCode};
    use keygo_dpop::testing::TestSigner;
    use keygo_dpop::{DpopProver, Htm, ProofRequest};
    use keygo_opaque::server::ServerSetup;
    use serde_json::{Value, json};
    use sqlx::PgPool;

    const LOGOUT: &str = "/api/v1/auth/logout";

    async fn signed_in(db: &PgPool, prover: &DpopProver<TestSigner>) -> (AppState, Issued) {
        let state = app_state(db, &ServerSetup::generate());
        let (user_id, device_id) = insert_device(db).await;
        let issued = issue_session(db, user_id, device_id, prover.jkt()).await;
        (state, issued)
    }

    fn logout_request(
        state: &AppState,
        prover: &DpopProver<TestSigner>,
        access_token: &str,
        nonce: Option<&str>,
    ) -> Request<Body> {
        let htu = state.public_url.join(LOGOUT).unwrap();
        let proof = prover
            .proof(&ProofRequest {
                htm: Htm::Post,
                htu: htu.as_str(),
                access_token: Some(access_token),
                nonce,
            })
            .unwrap();
        Request::post(LOGOUT)
            .header(AUTHORIZATION, format!("DPoP {access_token}"))
            .header("dpop", proof)
            .body(Body::empty())
            .unwrap()
    }

    /// Logs out with a fresh proof and answers a nonce challenge once, like a real client.
    async fn logout(
        state: &AppState,
        prover: &DpopProver<TestSigner>,
        access_token: &str,
    ) -> (StatusCode, Value) {
        let (status, headers, body) =
            send(state, logout_request(state, prover, access_token, None)).await;
        if body["code"] != "use_dpop_nonce" {
            return (status, body);
        }
        let nonce = headers["dpop-nonce"].to_str().unwrap();
        let (status, _, body) = send(
            state,
            logout_request(state, prover, access_token, Some(nonce)),
        )
        .await;
        (status, body)
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
