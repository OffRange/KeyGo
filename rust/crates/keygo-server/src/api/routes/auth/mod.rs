use crate::api::AppState;
use axum::Router;
use axum::extract::DefaultBodyLimit;

mod handshake;
mod login;
mod logout;
mod register;
mod session;
mod step_up;
mod token;

/// Every auth endpoint is reachable without a token, so a body beyond the largest legitimate one (a
/// key bundle of a few hundred bytes) is refused before it is buffered.
pub const MAX_BODY_BYTES: usize = 16 * 1024;

pub fn router() -> Router<AppState> {
    Router::new()
        .nest("/register", register::router())
        .nest("/login", login::router())
        .nest("/step-up", step_up::router())
        .nest("/token", token::router())
        .nest("/logout", logout::router())
        .layer(DefaultBodyLimit::max(MAX_BODY_BYTES))
}
