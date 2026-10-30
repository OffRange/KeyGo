use crate::api::{AppState, router};
use crate::cache::LoginStates;
use crate::db::{devices, invites, sessions, users};
use crate::domain::{
    AccessTokenKey, Device, DeviceId, DeviceInfo, InviteHash, InvitePepper, SessionId, TokenIssuer,
    UserId, Username,
};
use crate::service::invite;
use crate::service::login::{self, FinishLogin, StartLogin};
use crate::service::registration::{self, FinishRegistration, Signup, StartRegistration};
use crate::service::session::{self, Issued};
use axum::body::Body;
use axum::http::{HeaderMap, Request, StatusCode};
use http_body_util::BodyExt;
use keygo_dpop::DpopVerifier;
use keygo_dpop::testing::TestSigner;
use keygo_dpop::{DpopProver, Htm, ProofRequest};
use keygo_opaque::client::{ClientLogin, ClientRegistration};
use keygo_opaque::server::ServerSetup;
use serde_json::{Value, json};
use sqlx::{AssertSqlSafe, PgPool};
use std::sync::Arc;
use std::time::Duration;
use tower::ServiceExt;

pub const PASSWORD: &[u8] = b"correct horse battery staple";
pub const JKT: [u8; 32] = [7; 32];
pub const ISS: &str = "http://localhost";

pub fn token_issuer() -> TokenIssuer {
    TokenIssuer::new(AccessTokenKey::new(&[0; 32]), ISS)
}

pub fn username(raw: &str) -> Username {
    Username::parse(raw).unwrap()
}
pub fn device_json() -> Value {
    json!({ "name": "Pixel 9", "platform": "android", "app_version": "2.3.0" })
}
pub fn device_info() -> DeviceInfo {
    serde_json::from_value(device_json()).unwrap()
}
pub fn device(id: Option<DeviceId>) -> Device {
    Device {
        id,
        info: device_info(),
    }
}

pub async fn register(db: &PgPool, setup: &ServerSetup, name: &str) -> UserId {
    let user_id = UserId::random();
    let (client, registration_request) = ClientRegistration::start(PASSWORD).unwrap();

    let input = StartRegistration {
        username: username(name),
        user_id,
        invite: None,
        registration_request,
    };
    let started = registration::start(db, setup, &Signup::Open, input)
        .await
        .unwrap();

    let finish = client
        .finish(PASSWORD, started.registration_response)
        .unwrap();
    let input = FinishRegistration {
        registration_id: started.registration_id,
        registration_upload: finish.upload,
        device_info: device_info(),
        jkt: JKT,
    };
    registration::finish(db, &token_issuer(), input)
        .await
        .unwrap();

    user_id
}
pub async fn insert_device(db: &PgPool) -> (UserId, DeviceId) {
    let user_id = UserId::random();
    let device_id = DeviceId::random();
    let mut conn = db.acquire().await.unwrap();
    users::insert(&mut conn, user_id, &username("ada"))
        .await
        .unwrap();
    devices::insert(&mut conn, device_id, user_id, &device_info())
        .await
        .unwrap();
    (user_id, device_id)
}
pub async fn insert_session(db: &PgPool) -> (DeviceId, SessionId) {
    let (_, device_id) = insert_device(db).await;
    (device_id, open_session(db, device_id).await)
}
pub async fn open_session(db: &PgPool, device_id: DeviceId) -> SessionId {
    let session_id = SessionId::random();
    let mut conn = db.acquire().await.unwrap();
    sessions::insert(
        &mut conn,
        session_id,
        device_id,
        &JKT,
        0,
        Duration::from_hours(24),
    )
    .await
    .unwrap();
    session_id
}
pub async fn issue_session(
    db: &PgPool,
    user_id: UserId,
    device_id: DeviceId,
    jkt: &[u8; 32],
) -> Issued {
    let mut tx = db.begin().await.unwrap();
    let issued = session::issue(&mut tx, &token_issuer(), user_id, device_id, jkt)
        .await
        .unwrap();
    tx.commit().await.unwrap();
    issued
}
pub async fn start_login(
    db: &PgPool,
    login_states: &LoginStates,
    setup: &ServerSetup,
    name: &str,
) -> (ClientLogin, login::Started) {
    let (client, credential_request) = ClientLogin::start(PASSWORD).unwrap();
    let input = StartLogin {
        username: username(name),
        credential_request,
    };
    (
        client,
        login::start(db, login_states, setup, input).await.unwrap(),
    )
}
pub fn finish_login(
    client: ClientLogin,
    started: login::Started,
    device_id: Option<DeviceId>,
) -> FinishLogin {
    let finish = client
        .finish(PASSWORD, started.credential_response)
        .unwrap();
    FinishLogin {
        login_id: started.login_id,
        credential_finalization: finish.finalization,
        device: device(device_id),
        binding: finish.session_key.dpop_binding(&JKT),
        jkt: JKT,
    }
}

pub fn invite_hash(prefix: &[u8]) -> InviteHash {
    let mut hash = [0; 32];
    hash[..prefix.len()].copy_from_slice(prefix);
    hash.into()
}
pub async fn insert_invite(db: &PgPool, hash: InviteHash) {
    let mut conn = db.acquire().await.unwrap();
    invites::insert(&mut conn, &hash, Duration::from_hours(24))
        .await
        .unwrap();
}

const INVITE_PEPPER: [u8; 32] = [1; 32];

pub fn invite_only() -> Signup {
    Signup::InviteOnly(Arc::new(InvitePepper::new(&INVITE_PEPPER)))
}
pub async fn create_invite(db: &PgPool) -> String {
    let pepper = InvitePepper::new(&INVITE_PEPPER);
    let created = invite::create(db, &pepper, Duration::from_hours(24))
        .await
        .unwrap();
    created.code.to_string()
}

pub async fn count(db: &PgPool, table: &str) -> i64 {
    sqlx::query_scalar(AssertSqlSafe(format!("select count(*) from {table}")))
        .fetch_one(db)
        .await
        .unwrap()
}
pub async fn expire(db: &PgPool, table: &str) {
    sqlx::query(AssertSqlSafe(format!(
        "update {table} set expires_at = now() - interval '1 second'"
    )))
    .execute(db)
    .await
    .unwrap();
}
pub async fn expires_in_secs(db: &PgPool, table: &str) -> f64 {
    sqlx::query_scalar(AssertSqlSafe(format!(
        "select extract(epoch from max(expires_at) - now())::float8 from {table}"
    )))
    .fetch_one(db)
    .await
    .unwrap()
}

pub fn assert_expires_after(remaining: f64, ttl: Duration) {
    assert!(
        (ttl.as_secs_f64() - 5.0..=ttl.as_secs_f64()).contains(&remaining),
        "{remaining}"
    );
}

/// Requests that must see each other's login states share one `AppState` through `post_to`.
pub fn app_state(db: &PgPool, setup: &ServerSetup) -> AppState {
    AppState {
        public_url: url::Url::parse(ISS).unwrap(),
        signup: Signup::Open,
        opaque_state: setup.clone(),
        db: db.clone(),
        login_states: LoginStates::new(),
        dpop_verifier: Arc::new(DpopVerifier::new()),
        token_issuer: Arc::new(token_issuer()),
    }
}
pub async fn post(db: &PgPool, setup: &ServerSetup, uri: &str, body: Value) -> (StatusCode, Value) {
    post_to(app_state(db, setup), uri, body).await
}
pub async fn post_to(state: AppState, uri: &str, body: Value) -> (StatusCode, Value) {
    let (status, _, body) = send(&state, json_post(uri, &body)).await;
    (status, body)
}

pub fn prover() -> DpopProver<TestSigner> {
    DpopProver::new(TestSigner::new())
}

/// Posts with a fresh proof and answers a nonce challenge once, the way a real client would.
pub async fn post_with_proof(
    state: &AppState,
    prover: &DpopProver<TestSigner>,
    uri: &str,
    body: Value,
) -> (StatusCode, Value) {
    let request = proof_post(state, prover, uri, &body, None);
    let (status, headers, response) = send(state, request).await;
    if response["code"] != "use_dpop_nonce" {
        return (status, response);
    }

    let nonce = headers["dpop-nonce"].to_str().unwrap();
    let request = proof_post(state, prover, uri, &body, Some(nonce));
    let (status, _, response) = send(state, request).await;
    (status, response)
}
pub fn proof_post(
    state: &AppState,
    prover: &DpopProver<TestSigner>,
    uri: &str,
    body: &Value,
    nonce: Option<&str>,
) -> Request<Body> {
    let htu = state.public_url.join(uri).unwrap();
    let proof = prover
        .proof(&ProofRequest {
            htm: Htm::Post,
            htu: htu.as_str(),
            access_token: None,
            nonce,
        })
        .unwrap();
    let mut request = json_post(uri, body);
    request.headers_mut().insert("dpop", proof.parse().unwrap());
    request
}

/// Signs one proof carrying the verifier's nonce and builds identical requests around it, the way
/// an attacker replays a captured request. Fetching the nonce takes a challenge, which keeps state.
pub async fn replayable_post(
    state: &AppState,
    prover: &DpopProver<TestSigner>,
    uri: &str,
    body: &Value,
) -> impl Fn() -> Request<Body> + use<> {
    let (_, headers, challenge) = send(state, proof_post(state, prover, uri, body, None)).await;
    assert_eq!(challenge["code"], "use_dpop_nonce");
    let nonce = headers["dpop-nonce"].to_str().unwrap();
    let proof = proof_post(state, prover, uri, body, Some(nonce)).headers()["dpop"].clone();
    let (uri, body) = (uri.to_owned(), body.clone());

    move || {
        let mut request = json_post(&uri, &body);
        request.headers_mut().insert("dpop", proof.clone());
        request
    }
}

pub async fn send(state: &AppState, request: Request<Body>) -> (StatusCode, HeaderMap, Value) {
    let response = router(state.clone()).oneshot(request).await.unwrap();
    let status = response.status();
    let headers = response.headers().clone();
    let body = response.into_body().collect().await.unwrap().to_bytes();
    (status, headers, serde_json::from_slice(&body).unwrap())
}
fn json_post(uri: &str, body: &Value) -> Request<Body> {
    Request::post(uri)
        .header("content-type", "application/json")
        .body(Body::from(body.to_string()))
        .unwrap()
}
