use crate::api::{AppState, router};
use crate::cache::LoginStates;
use crate::db::invites;
use crate::domain::{InviteHash, InvitePepper, Username};
use crate::service::invite;
use crate::service::login::{self, FinishLogin, StartLogin};
use crate::service::registration::{self, FinishRegistration, Signup, StartRegistration};
use axum::body::Body;
use axum::http::{Request, StatusCode};
use http_body_util::BodyExt;
use keygo_dpop::NonceKey;
use keygo_dpop::verify::DpopVerifier;
use keygo_opaque::client::{ClientLogin, ClientRegistration};
use keygo_opaque::server::ServerSetup;
use serde_json::Value;
use sqlx::{AssertSqlSafe, PgPool};
use std::sync::Arc;
use std::time::Duration;
use tower::ServiceExt;
use uuid::Uuid;

pub const PASSWORD: &[u8] = b"correct horse battery staple";

pub fn username(raw: &str) -> Username {
    Username::parse(raw).unwrap()
}
pub async fn register(db: &PgPool, setup: &ServerSetup, name: &str) -> Uuid {
    let user_id = Uuid::new_v4();
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
    };
    registration::finish(db, input).await.unwrap();

    user_id
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
pub fn finish_login(client: ClientLogin, started: login::Started) -> FinishLogin {
    let finish = client
        .finish(PASSWORD, started.credential_response)
        .unwrap();
    FinishLogin {
        login_id: started.login_id,
        credential_finalization: finish.finalization,
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
    Signup::InviteOnly(Arc::new(InvitePepper::new(INVITE_PEPPER)))
}
pub async fn create_invite(db: &PgPool) -> String {
    let pepper = InvitePepper::new(INVITE_PEPPER);
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

/// Requests that must see each other's login states share one `AppState` through `post_to`.
pub fn app_state(db: &PgPool, setup: &ServerSetup) -> AppState {
    AppState {
        public_url: url::Url::parse("http://localhost").unwrap(),
        signup: Signup::Open,
        opaque_state: setup.clone(),
        db: db.clone(),
        login_states: LoginStates::new(),
        dpop_verifier: Arc::new(DpopVerifier::new(NonceKey::new([0; 32]))),
    }
}
pub async fn post(db: &PgPool, setup: &ServerSetup, uri: &str, body: Value) -> (StatusCode, Value) {
    post_to(app_state(db, setup), uri, body).await
}
pub async fn post_to(state: AppState, uri: &str, body: Value) -> (StatusCode, Value) {
    let request = Request::post(uri)
        .header("content-type", "application/json")
        .body(Body::from(body.to_string()))
        .unwrap();
    let response = router(state).oneshot(request).await.unwrap();
    let status = response.status();
    let body = response.into_body().collect().await.unwrap().to_bytes();
    (status, serde_json::from_slice(&body).unwrap())
}
