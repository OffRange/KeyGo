mod authenticated;
mod dpop;
mod error;
mod extract;
mod routes;
mod state;

use axum::http::header::AsHeaderName;
use axum::http::{HeaderMap, HeaderValue};
pub use error::{ErrorCode, HttpError, NotFound, ServerError, ServerResult};
pub use extract::Json;
pub use state::AppState;

use axum::Router;
use axum::http::header::CACHE_CONTROL;
use tower_http::set_header::SetResponseHeaderLayer;
use tower_http::trace::TraceLayer;

pub fn router(state: AppState) -> Router {
    Router::new()
        .nest("/health", routes::health::router())
        .nest("/api/v1", v1())
        .fallback(not_found)
        .layer(SetResponseHeaderLayer::if_not_present(
            CACHE_CONTROL,
            HeaderValue::from_static("no-store"),
        ))
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

fn v1() -> Router<AppState> {
    Router::new()
        .nest("/meta", routes::meta::router())
        .nest("/auth", routes::auth::router())
}

async fn not_found() -> ServerError {
    NotFound.into()
}

fn single_header<A: AsHeaderName>(headers: &HeaderMap, name: A) -> Option<&HeaderValue> {
    let mut values = headers.get_all(name).iter();
    match (values.next(), values.next()) {
        (Some(value), None) => Some(value),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cache::LoginStates;
    use crate::domain::InvitePepper;
    use crate::service::registration::Signup;
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use http_body_util::BodyExt;
    use keygo_dpop::verify::DpopVerifier;
    use routes::auth::MAX_BODY_BYTES;
    use serde_json::{Value, json};
    use std::sync::Arc;
    use tower::ServiceExt;

    async fn get(state: AppState, uri: &str) -> (StatusCode, Vec<u8>) {
        let request = Request::get(uri).body(Body::empty()).unwrap();
        let response = router(state).oneshot(request).await.unwrap();
        let status = response.status();
        let body = response.into_body().collect().await.unwrap().to_bytes();
        (status, body.to_vec())
    }

    async fn post_json(state: AppState, uri: &str, body: Value) -> (StatusCode, Vec<u8>) {
        let request = Request::post(uri)
            .header("content-type", "application/json")
            .body(Body::from(body.to_string()))
            .unwrap();
        let response = router(state).oneshot(request).await.unwrap();
        let status = response.status();
        let body = response.into_body().collect().await.unwrap().to_bytes();
        (status, body.to_vec())
    }

    pub(super) fn state() -> AppState {
        AppState {
            public_url: url::Url::parse("http://localhost").unwrap(),
            signup: Signup::InviteOnly(Arc::new(InvitePepper::new(&[0; 32]))),
            opaque_state: keygo_opaque::server::ServerSetup::generate(),
            // Lazy pools never connect until queried, so routes without a query need no database.
            db: sqlx::postgres::PgPoolOptions::new()
                .connect_lazy_with(sqlx::postgres::PgConnectOptions::new()),
            login_states: LoginStates::new(),
            dpop_verifier: Arc::new(DpopVerifier::new()),
            token_issuer: Arc::new(crate::testing::token_issuer()),
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
            signup: Signup::Open,
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
        let (status, body) = get(state(), "/api/v1/nope").await;

        assert_eq!(status, StatusCode::NOT_FOUND);
        let body: Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(body["code"], "not_found");
    }

    #[tokio::test]
    async fn invalid_body_is_reported_as_json() {
        let (status, body) = post_json(
            state(),
            "/api/v1/auth/register/start",
            json!({ "username": "a", "user_id": "not-a-uuid" }),
        )
        .await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        let body: Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(body["code"], "invalid_request");
        assert!(body["message"].as_str().is_some_and(|m| !m.is_empty()));
    }

    #[tokio::test]
    async fn error_responses_are_not_stored() {
        for request in [
            Request::get("/api/v1/nope").body(Body::empty()).unwrap(),
            Request::post("/api/v1/auth/register/start")
                .body(Body::from("{}"))
                .unwrap(),
        ] {
            let response = router(state()).oneshot(request).await.unwrap();

            assert!(response.status().is_client_error());
            assert_eq!(response.headers()[CACHE_CONTROL], "no-store");
        }
    }

    #[tokio::test]
    async fn oversized_body_is_rejected_as_json() {
        let padding = "a".repeat(MAX_BODY_BYTES);
        let (status, body) = post_json(
            state(),
            "/api/v1/auth/register/start",
            json!({ "username": padding }),
        )
        .await;

        assert_eq!(status, StatusCode::PAYLOAD_TOO_LARGE);
        let body: Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(body["code"], "invalid_request");
    }

    #[tokio::test]
    async fn body_at_the_limit_is_parsed() {
        let body = json!({ "username": "" }).to_string();
        let padded = format!("{body:<width$}", width = MAX_BODY_BYTES);
        let request = Request::post("/api/v1/auth/register/start")
            .header("content-type", "application/json")
            .body(Body::from(padded))
            .unwrap();
        let response = router(state()).oneshot(request).await.unwrap();

        // Gets past the limit and fails on the missing fields instead.
        assert_eq!(response.status(), StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn missing_content_type_is_reported_as_json() {
        let request = Request::post("/api/v1/auth/register/start")
            .body(Body::from("{}"))
            .unwrap();
        let response = router(state()).oneshot(request).await.unwrap();

        assert_eq!(response.status(), StatusCode::UNSUPPORTED_MEDIA_TYPE);
        let body = response.into_body().collect().await.unwrap().to_bytes();
        let body: Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(body["code"], "invalid_request");
    }
}
