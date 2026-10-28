use crate::api::AppState;
use crate::api::routes::auth::token::token;
use axum::Router;
use axum::extract::DefaultBodyLimit;
use axum::routing::post;

mod login;
mod register;
mod session;
mod token;

/// The largest legitimate body is a key bundle of a few hundred bytes. Every auth endpoint is
/// reachable without a token, so anything larger is refused before it is buffered and parsed.
/// Scoped to `/auth` so routes with larger payloads set their own limit.
pub const MAX_BODY_BYTES: usize = 16 * 1024;

pub fn router() -> Router<AppState> {
    Router::new()
        .nest("/register", register::router())
        .nest("/login", login::router())
        .route("/token", post(token))
        .layer(DefaultBodyLimit::max(MAX_BODY_BYTES))
}
