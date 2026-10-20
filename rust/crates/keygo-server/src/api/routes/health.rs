use crate::api::AppState;
use axum::Router;
use axum::http::StatusCode;
use axum::routing::get;

pub fn router() -> Router<AppState> {
    Router::new().route("/live", get(live))
}

async fn live() -> StatusCode {
    StatusCode::OK
}
