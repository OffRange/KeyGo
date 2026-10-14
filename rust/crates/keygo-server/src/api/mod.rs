mod routes;
mod state;

pub use state::AppState;

use axum::Router;
use tower_http::trace::TraceLayer;

pub fn router(state: AppState) -> Router {
    Router::new()
        .nest("/health", routes::health::router())
        .nest("/api/v1", v1())
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

fn v1() -> Router<AppState> {
    Router::new()
        .nest("/meta", routes::meta::router())
        .nest("/auth", routes::auth::router())
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use http_body_util::BodyExt;
    use serde_json::{Value, json};
    use tower::ServiceExt;

    async fn get(state: AppState, uri: &str) -> (StatusCode, Vec<u8>) {
        let request = Request::get(uri).body(Body::empty()).unwrap();
        let response = router(state).oneshot(request).await.unwrap();
        let status = response.status();
        let body = response.into_body().collect().await.unwrap().to_bytes();
        (status, body.to_vec())
    }

    fn state() -> AppState {
        AppState {
            allow_signups: false,
            opaque_state: keygo_opaque::server::ServerSetup::generate(),
        }
    }

    #[tokio::test]
    async fn liveness_probe_is_ok() {
        let (status, _) = get(state(), "/health/live").await;

        assert_eq!(status, StatusCode::OK);
    }

    #[tokio::test]
    async fn server_info_reports_version_and_signups() {
        let state = AppState {
            allow_signups: true,
            ..state()
        };

        let (status, body) = get(state, "/api/v1/meta").await;

        assert_eq!(status, StatusCode::OK);
        let body: Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(
            body,
            json!({ "version": env!("CARGO_PKG_VERSION"), "allow_signups": true }),
        );
    }

    #[tokio::test]
    async fn unknown_route_is_not_found() {
        let (status, _) = get(state(), "/api/v1/nope").await;

        assert_eq!(status, StatusCode::NOT_FOUND);
    }
}
