use crate::api::authenticated::FreshAuthenticated;
use crate::api::{AppState, ServerResult};
use crate::service::account;
use axum::Router;
use axum::extract::State;
use axum::routing::delete;

pub fn router() -> Router<AppState> {
    Router::new().route("/", delete(delete_account))
}

async fn delete_account(
    State(state): State<AppState>,
    authenticated: FreshAuthenticated,
) -> ServerResult<()> {
    account::delete(&state.db, authenticated.claims().user_id()).await?;
    Ok(())
}
