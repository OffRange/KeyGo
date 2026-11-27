use crate::api::authenticated::FreshAuthenticated;
use crate::api::{AppState, ServerResult};
use crate::service::account;
use axum::Router;
use axum::extract::State;
use axum::http::StatusCode;
use axum::routing::delete;

pub fn router() -> Router<AppState> {
    Router::new().route("/", delete(delete_account))
}

async fn delete_account(
    State(state): State<AppState>,
    authenticated: FreshAuthenticated,
) -> ServerResult<StatusCode> {
    account::delete(&state.db, authenticated.claims().user_id()).await?;
    Ok(StatusCode::NO_CONTENT)
}

#[cfg(test)]
mod tests {
    use crate::domain::{FRESH_AUTH_WINDOW, unix_now};
    use crate::testing::{count, delete_with_token, prover, signed_in, token_issuer};
    use axum::http::StatusCode;
    use sqlx::PgPool;

    const URI: &str = "/api/v1/account";

    #[sqlx::test]
    async fn fresh_session_deletes_the_account(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;
        let token = issued.access_token.expose();

        let (status, body) = delete_with_token(&state, &prover, URI, token).await;

        assert_eq!(status, StatusCode::NO_CONTENT);
        assert!(body.is_null());
        assert_eq!(count(&db, "users").await, 0);
    }

    #[sqlx::test]
    async fn deleted_account_revokes_its_tokens(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;
        let token = issued.access_token.expose();
        delete_with_token(&state, &prover, URI, token).await;

        let (status, body) = delete_with_token(&state, &prover, URI, token).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "session_revoked");
        assert!(body.get("reason").is_none());
    }

    #[sqlx::test]
    async fn stale_session_cannot_delete_the_account(db: PgPool) {
        let prover = prover();
        let (state, issued) = signed_in(&db, &prover).await;
        let issuer = token_issuer();
        let claims = issuer.verify(issued.access_token.expose()).unwrap();
        let auth_time = unix_now() - FRESH_AUTH_WINDOW.as_secs() - 1;
        let stale = issuer.issue(
            claims.user_id(),
            claims.session_id(),
            auth_time,
            prover.jkt(),
        );

        let (status, body) = delete_with_token(&state, &prover, URI, stale.expose()).await;

        assert_eq!(status, StatusCode::FORBIDDEN);
        assert_eq!(body["code"], "fresh_auth_required");
        assert_eq!(count(&db, "users").await, 1);
    }
}
