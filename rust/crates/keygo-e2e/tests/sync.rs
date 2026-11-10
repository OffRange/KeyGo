use async_trait::async_trait;
use axum::body::Body;
use axum::http::{HeaderName, HeaderValue, Request};
use http_body_util::BodyExt;
use keygo_api::domain::{Device, RevokeReason};
use keygo_client::ark_session::ArkSession;
use keygo_client::sync::testing::{FakeSigner, FakeTokenStore, FixedClock};
use keygo_client::sync::{
    Clock, Header, HttpRequest, HttpResponse, StoredSession, SyncClient, SyncError, SystemClock,
    Transport, TransportError,
};
use keygo_core::{AccountRootKey, KeyMaterial, Sealed, ServerKek, UserId, Wraps};
use keygo_dpop::testing::TestSigner;
use keygo_opaque::server::ServerSetup;
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
        Arc::new(FakeSigner::new()),
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

/// A client with nothing stored, against a server with no session for it.
fn signed_out(db: &PgPool) -> (SyncClient, Arc<FakeTokenStore>, AppState) {
    let state = testing::app_state(db, &ServerSetup::generate());
    let store = Arc::new(FakeTokenStore::new());
    let client = SyncClient::new(
        Url::parse(testing::ISS).unwrap(),
        Arc::new(TowerTransport(state.clone())),
        Arc::new(FakeSigner::new()),
        store.clone(),
    )
    .unwrap();
    (client, store, state)
}

fn local_account() -> (ArkSession, UserId) {
    let ark = ArkSession::new();
    let user_id = ark.create_account(password()).unwrap().user_id;
    (ark, user_id)
}

async fn register(client: &SyncClient, ark: &ArkSession, user_id: UserId, name: &str) {
    client
        .register(
            ark,
            user_id,
            name.into(),
            password(),
            None,
            testing::device_info(),
        )
        .await
        .unwrap();
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
async fn register_signs_the_device_in(db: PgPool) {
    let (client, store, _) = signed_out(&db);
    let (ark, user_id) = local_account();

    let account = client
        .register(
            &ark,
            user_id,
            "Ada".into(),
            password(),
            None,
            testing::device_info(),
        )
        .await
        .unwrap();

    assert_eq!(account.user_id, user_id);
    assert_eq!(account.username.as_str(), "ada");
    assert!(store.current_refresh_token().is_some());
    let devices = client.devices().await.unwrap();
    assert_eq!(devices.len(), 1);
    assert_eq!(devices[0].id, account.device_id);
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn server_wrapped_ark_opens_after_sign_in(db: PgPool) {
    let (client, _, state) = signed_out(&db);
    let (ark, user_id) = local_account();
    register(&client, &ark, user_id, "ada").await;

    let (login, started) =
        testing::start_login(&db, &state.login_states, &state.opaque_state, "ada").await;
    let export_key = login
        .finish(testing::PASSWORD, started.credential_response)
        .unwrap()
        .export_key;
    let stored: Vec<u8> =
        sqlx::query_scalar("select wrapped_ark from key_bundles where user_id = $1")
            .bind(user_id.as_uuid())
            .fetch_one(&db)
            .await
            .unwrap();

    let opened = ServerKek::from_export_key(export_key.as_bytes(), user_id)
        .unwrap()
        .unwrap_key(
            &Sealed::<AccountRootKey>::from_bytes(&stored).unwrap(),
            &user_id,
        )
        .unwrap();

    assert_eq!(opened.as_bytes(), ark.export_ark().unwrap().as_slice());
}

#[sqlx::test(migrations = "../keygo-server/migrations")]
async fn registering_while_signed_in_ends_the_old_session(db: PgPool) {
    let h = harness(&db).await;
    let (ark, user_id) = local_account();

    register(&h.client, &ark, user_id, "bob").await;

    let (status, _) = testing::post_with_token(
        &h.state,
        &h.prover,
        "/api/v1/auth/logout",
        &h.access_token,
        serde_json::Value::Null,
    )
    .await;
    assert!(!status.is_success(), "{status}");
    assert_ne!(h.store.current_refresh_token().unwrap(), h.seed_token);
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
