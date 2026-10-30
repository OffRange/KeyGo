use crate::api::AppState;
use crate::service::registration::Signup;
use axum::extract::State;
use axum::routing::get;
use axum::{Json, Router};
use serde::Serialize;

pub fn router() -> Router<AppState> {
    Router::new().route("/", get(info))
}

#[derive(Serialize)]
struct ServerInfo {
    version: &'static str,
    public_url: String,
    allow_signups: bool,
}

/// What a client needs to know before talking to this server.
async fn info(State(state): State<AppState>) -> Json<ServerInfo> {
    Json(ServerInfo {
        version: env!("CARGO_PKG_VERSION"),
        public_url: state.public_url.to_string(),
        allow_signups: matches!(state.signup, Signup::Open),
    })
}
