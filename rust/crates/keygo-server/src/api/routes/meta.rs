use crate::api::AppState;
use crate::service::registration::Signup;
use axum::extract::State;
use axum::routing::get;
use axum::{Json, Router};
use keygo_api::meta::ServerInfo;

pub fn router() -> Router<AppState> {
    Router::new().route("/", get(info))
}

/// What a client needs to know before talking to this server.
async fn info(State(state): State<AppState>) -> Json<ServerInfo> {
    Json(ServerInfo {
        version: env!("CARGO_PKG_VERSION").to_owned(),
        public_url: state.public_url.to_string(),
        allow_signups: matches!(state.signup, Signup::Open),
    })
}
