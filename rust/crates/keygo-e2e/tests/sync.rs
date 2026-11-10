use async_trait::async_trait;
use axum::body::Body;
use axum::http::{HeaderName, HeaderValue, Request};
use http_body_util::BodyExt;
use keygo_api::domain::{Device, RevokeReason};
use keygo_client::sync::testing::{FakeDpopKey, FakeTokenStore, FixedClock};
use keygo_client::sync::{
    Clock, Header, HttpRequest, HttpResponse, StoredSession, SyncClient, SyncError, SystemClock,
    Transport, TransportError,
};
use keygo_dpop::testing::TestSigner;
use keygo_server::api::{AppState, router};
use keygo_server::testing;
use sqlx::PgPool;
use std::sync::Arc;
use tower::ServiceExt;
use url::Url;

struct TowerTransport(AppState);

#[async_trait]
impl Transport for TowerTransport {
    async fn send(&self, request: HttpRequest) -> Result<HttpResponse, TransportError> {
        let fail = |e: &dyn std::fmt::Display| TransportError {
            message: e.to_string(),
        };
        let mut built = Request::builder()
            .method(request.method.as_str())
            .uri(&request.url);
        for header in &request.headers {
            built = built.header(
                HeaderName::try_from(header.name.as_str()).map_err(|e| fail(&e))?,
                HeaderValue::try_from(header.value.as_str()).map_err(|e| fail(&e))?,
            );
        }
        let built = built.body(Body::from(request.body)).map_err(|e| fail(&e))?;

        let response = router(self.0.clone())
            .oneshot(built)
            .await
            .map_err(|e| fail(&e))?;
        let status = response.status().as_u16();
        let headers = response
            .headers()
            .iter()
            .map(|(name, value)| Header {
                name: name.as_str().to_owned(),
                value: value.to_str().unwrap_or_default().to_owned(),
            })
            .collect();
        let body = response
            .into_body()
            .collect()
            .await
            .map_err(|e| fail(&e))?
            .to_bytes()
            .to_vec();
        Ok(HttpResponse {
            status,
            headers,
            body,
        })
    }
}

struct Harness {
    client: SyncClient,
    store: Arc<FakeTokenStore>,
    clock: Arc<FixedClock>,
    state: AppState,
    prover: keygo_dpop::DpopProver<TestSigner>,
    device_id: keygo_core::DeviceId,
    access_token: String,
    seed_token: String,
}

async fn harness(db: &PgPool) -> Harness {
    let prover = testing::prover();
    let (state, issued) = testing::signed_in(db, &prover).await;
    let seed_token = issued.refresh_token.expose_encoded().to_string();
    let access_token = issued.access_token.expose().to_owned();
    let device_id = issued.device_id;

    let store = Arc::new(FakeTokenStore::new());
    store.seed(StoredSession {
        device_id,
        refresh_token: issued.refresh_token,
        refresh_expires_at: SystemClock.now_secs() + issued.refresh_expires_in.as_secs(),
    });
    let clock = FixedClock::new(SystemClock.now_secs());
    let client = SyncClient::new(
        Url::parse(testing::ISS).unwrap(),
        Arc::new(TowerTransport(state.clone())),
        Arc::new(FakeDpopKey::new()),
        store.clone(),
    )
    .unwrap()
    .with_clock(clock.clone());

    Harness {
        client,
        store,
        clock,
        state,
        prover,
        device_id,
        access_token,
        seed_token,
    }
}

fn password() -> &'static str {
    std::str::from_utf8(testing::PASSWORD).unwrap()
}

fn other(devices: &[Device], mine: keygo_core::DeviceId) -> keygo_core::DeviceId {
    devices.iter().find(|d| d.id != mine).unwrap().id
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn meta_reports_the_server(db: PgPool) {
    let h = harness(&db).await;

    let info = h.client.meta().await.unwrap();

    assert!(info.allow_signups);
    assert_eq!(info.public_url, "http://localhost/");
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn first_call_refreshes_and_rotates(db: PgPool) {
    let h = harness(&db).await;

    assert_eq!(h.client.devices().await.unwrap().len(), 2);

    assert_ne!(h.store.current_refresh_token().unwrap(), h.seed_token);
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn expired_access_token_refreshes_again(db: PgPool) {
    let h = harness(&db).await;
    h.client.devices().await.unwrap();
    let first = h.store.current_refresh_token().unwrap();

    h.clock.advance(601);
    h.client.devices().await.unwrap();

    assert_ne!(h.store.current_refresh_token().unwrap(), first);
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn revoked_session_signs_out(db: PgPool) {
    let h = harness(&db).await;
    let (status, _) = testing::post_with_token(
        &h.state,
        &h.prover,
        "/api/v1/auth/logout",
        &h.access_token,
        serde_json::Value::Null,
    )
    .await;
    assert!(status.is_success(), "{status}");

    assert_eq!(
        h.client.devices().await.unwrap_err(),
        SyncError::SignedOut {
            reason: Some(RevokeReason::Logout),
        },
    );
    assert_eq!(h.store.current_refresh_token(), None);
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn fresh_auth_required_then_step_up_then_retry(db: PgPool) {
    let h = harness(&db).await;
    sqlx::query("update sessions set auth_time = to_timestamp($1) where device_id = $2")
        .bind((SystemClock.now_secs() - 301) as f64)
        .bind(h.device_id.as_uuid())
        .execute(&db)
        .await
        .unwrap();

    let devices = h.client.devices().await.unwrap();
    let other = other(&devices, h.device_id);
    assert_eq!(
        h.client.delete_device(other).await.unwrap_err(),
        SyncError::FreshAuthRequired,
    );

    h.client.step_up(password()).await.unwrap();
    h.client.delete_device(other).await.unwrap();

    assert_eq!(h.client.devices().await.unwrap().len(), 1);
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn step_up_replaces_the_access_token_without_rotating(db: PgPool) {
    let h = harness(&db).await;
    h.client.devices().await.unwrap();
    let stored = h.store.current_refresh_token().unwrap();

    h.client.step_up(password()).await.unwrap();
    h.client.devices().await.unwrap();

    assert_eq!(h.store.current_refresh_token().unwrap(), stored);
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn wrong_step_up_password_is_invalid_credentials(db: PgPool) {
    let h = harness(&db).await;

    assert_eq!(
        h.client.step_up("wrong").await.unwrap_err(),
        SyncError::InvalidCredentials,
    );
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn empty_step_up_password_is_invalid_credentials(db: PgPool) {
    let h = harness(&db).await;

    assert_eq!(
        h.client.step_up("").await.unwrap_err(),
        SyncError::InvalidCredentials,
    );
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn client_logout_ends_the_session_on_the_server(db: PgPool) {
    let h = harness(&db).await;

    h.client.logout().await.unwrap();
    assert_eq!(h.store.current_refresh_token(), None);

    let (status, _) = testing::post_with_token(
        &h.state,
        &h.prover,
        "/api/v1/auth/logout",
        &h.access_token,
        serde_json::Value::Null,
    )
    .await;
    assert!(!status.is_success(), "{status}");
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn refused_save_does_not_trip_reuse_detection(db: PgPool) {
    let h = harness(&db).await;
    h.store.fail_saves();

    h.client.devices().await.unwrap();
    h.clock.advance(601);
    h.client.devices().await.unwrap();

    assert_eq!(h.store.current_refresh_token().unwrap(), h.seed_token);
}
