use crate::api::AppState;
use axum::Router;

mod register;

pub fn router() -> Router<AppState> {
    Router::new().nest("/register", register::router())
}
