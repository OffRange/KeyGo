//! Wire identity: every JSON shape the API exchanges, written out by hand.
//! A failure here means a field name or shape changed, which breaks deployed clients.

use keygo_api::auth::handshake;
use keygo_api::auth::login;
use keygo_api::auth::register;
use keygo_api::auth::step_up;
use keygo_api::auth::token::TokenRequest;
use keygo_api::auth::{Access, Session};
use keygo_api::devices::Devices;
use keygo_api::domain::RefreshToken;
use keygo_api::meta::ServerInfo;
use keygo_opaque::client::{ClientLogin, ClientRegistration};
use keygo_opaque::server::{self, ServerLogin, ServerSetup};
use keygo_opaque::{
    CredentialFinalization, CredentialRequest, CredentialResponse, DpopBinding,
    RegistrationRequest, RegistrationResponse, RegistrationUpload,
};
use serde::Serialize;
use serde::de::DeserializeOwned;
use serde_json::{Value, json};

const NIL: &str = "00000000-0000-0000-0000-000000000000";
const HANDSHAKE_ID: &str = "_____________________w";
const REFRESH: &str = "kgr___________________________________________8";

fn pin<T: Serialize + DeserializeOwned>(value: &T, expected: Value) {
    assert_eq!(serde_json::to_value(value).unwrap(), expected);
    let decoded: T = serde_json::from_value(expected.clone()).unwrap();
    assert_eq!(serde_json::to_value(&decoded).unwrap(), expected);
}

fn from<T: DeserializeOwned>(value: Value) -> T {
    serde_json::from_value(value).unwrap()
}

fn wrapped_ark() -> String {
    "BwcH".repeat(20)
}

fn device() -> Value {
    json!({"name": "Pixel 9", "platform": "android", "app_version": "2.3.0"})
}

fn key_bundle() -> Value {
    json!({"wrapped_ark": wrapped_ark()})
}

fn session_json() -> Value {
    json!({
        "device_id": NIL,
        "access_token": "a.b.c",
        "access_expires_in": 900,
        "refresh_token": REFRESH,
        "refresh_expires_in": 2592000,
    })
}

fn sample_session() -> Session {
    from(session_json())
}

/// One real OPAQUE registration and login, with each message kept as its base64 text.
struct Messages {
    registration_request: (RegistrationRequest, String),
    registration_response: (RegistrationResponse, String),
    registration_upload: (RegistrationUpload, String),
    credential_request: (CredentialRequest, String),
    credential_response: (CredentialResponse, String),
    credential_finalization: (CredentialFinalization, String),
    dpop_binding: (DpopBinding, String),
}

fn messages() -> Messages {
    let password = b"correct horse battery staple";
    let setup = ServerSetup::generate();

    let (registration, request) = ClientRegistration::start(password).unwrap();
    let registration_request = request.to_base64();
    let response = server::registration_start(&setup, request, b"account-1").unwrap();
    let registration_response = response.to_base64();
    let finish = registration.finish(password, response).unwrap();
    let registration_upload = finish.upload.to_base64();
    let file = server::registration_finish(finish.upload);

    let (client, request) = ClientLogin::start(password).unwrap();
    let credential_request = request.to_base64();
    let (server, response) = ServerLogin::start(&setup, Some(file), request, b"account-1").unwrap();
    let credential_response = response.to_base64();
    let login = client.finish(password, response).unwrap();
    let credential_finalization = login.finalization.to_base64();
    let binding = login.session_key.dpop_binding(&[0x40; 32]);
    let dpop_binding = binding.to_base64();
    server.finish(login.finalization).unwrap();

    Messages {
        registration_request: (
            RegistrationRequest::from_base64(&registration_request).unwrap(),
            registration_request,
        ),
        registration_response: (
            RegistrationResponse::from_base64(&registration_response).unwrap(),
            registration_response,
        ),
        registration_upload: (
            RegistrationUpload::from_base64(&registration_upload).unwrap(),
            registration_upload,
        ),
        credential_request: (
            CredentialRequest::from_base64(&credential_request).unwrap(),
            credential_request,
        ),
        credential_response: (
            CredentialResponse::from_base64(&credential_response).unwrap(),
            credential_response,
        ),
        credential_finalization: (
            CredentialFinalization::from_base64(&credential_finalization).unwrap(),
            credential_finalization,
        ),
        dpop_binding: (binding, dpop_binding),
    }
}

#[test]
fn register_start_request() {
    let m = messages();
    let make = |invite: Option<&str>| register::StartRequest {
        username: "alice".into(),
        user_id: from(json!(NIL)),
        invite: invite.map(String::from),
        registration_request: RegistrationRequest::from_base64(&m.registration_request.1).unwrap(),
    };
    let expected = |invite: Value| {
        json!({
            "username": "alice",
            "user_id": NIL,
            "invite": invite,
            "registration_request": m.registration_request.1,
        })
    };
    pin(&make(Some("code")), expected(json!("code")));
    pin(&make(None), expected(Value::Null));
}

#[test]
fn register_start_response() {
    let m = messages();
    pin(
        &register::StartResponse {
            registration_id: from(json!(HANDSHAKE_ID)),
            registration_response: m.registration_response.0,
            expires_in: 300,
        },
        json!({
            "registration_id": HANDSHAKE_ID,
            "registration_response": m.registration_response.1,
            "expires_in": 300,
        }),
    );
}

#[test]
fn register_finish_request() {
    let m = messages();
    pin(
        &register::FinishRequest {
            registration_id: from(json!(HANDSHAKE_ID)),
            registration_upload: m.registration_upload.0,
            key_bundle: from(key_bundle()),
            device: from(device()),
        },
        json!({
            "registration_id": HANDSHAKE_ID,
            "registration_upload": m.registration_upload.1,
            "key_bundle": {"wrapped_ark": wrapped_ark()},
            "device": {"name": "Pixel 9", "platform": "android", "app_version": "2.3.0"},
        }),
    );
}

#[test]
fn register_finish_response() {
    pin(
        &register::FinishResponse {
            username: from(json!("alice")),
            session: sample_session(),
        },
        json!({"username": "alice", "session": session_json()}),
    );
}

#[test]
fn login_start_request() {
    let m = messages();
    pin(
        &login::StartRequest {
            username: "alice".into(),
            credential_request: m.credential_request.0,
        },
        json!({"username": "alice", "credential_request": m.credential_request.1}),
    );
}

#[test]
fn handshake_start_response() {
    let m = messages();
    pin(
        &handshake::StartResponse {
            login_id: from(json!(HANDSHAKE_ID)),
            credential_response: m.credential_response.0,
            expires_in: 60,
        },
        json!({
            "login_id": HANDSHAKE_ID,
            "credential_response": m.credential_response.1,
            "expires_in": 60,
        }),
    );
}

fn login_finish(device_id: Option<&str>) -> (login::FinishRequest, Value) {
    let m = messages();
    let mut claim = device();
    claim["device_id"] = json!(device_id);
    let request = login::FinishRequest {
        login_id: from(json!(HANDSHAKE_ID)),
        credential_finalization: m.credential_finalization.0,
        device: from(claim.clone()),
        dpop_binding: m.dpop_binding.0,
    };
    let expected = json!({
        "login_id": HANDSHAKE_ID,
        "credential_finalization": m.credential_finalization.1,
        "device": claim,
        "dpop_binding": m.dpop_binding.1,
    });
    (request, expected)
}

#[test]
fn login_finish_request_with_device() {
    let (request, expected) = login_finish(Some(NIL));
    assert_eq!(expected["device"]["device_id"], NIL);
    pin(&request, expected);
}

#[test]
fn login_finish_request_without_device() {
    let (request, expected) = login_finish(None);
    assert_eq!(expected["device"]["device_id"], Value::Null);
    pin(&request, expected);
}

#[test]
fn login_finish_response() {
    pin(
        &login::FinishResponse {
            user_id: from(json!(NIL)),
            username: from(json!("alice")),
            key_bundle: from(key_bundle()),
            session: sample_session(),
        },
        json!({
            "user_id": NIL,
            "username": "alice",
            "key_bundle": {"wrapped_ark": wrapped_ark()},
            "session": session_json(),
        }),
    );
}

#[test]
fn step_up_start_request() {
    let m = messages();
    pin(
        &step_up::StartRequest {
            credential_request: m.credential_request.0,
        },
        json!({"credential_request": m.credential_request.1}),
    );
}

#[test]
fn step_up_finish_request() {
    let m = messages();
    pin(
        &step_up::FinishRequest {
            login_id: from(json!(HANDSHAKE_ID)),
            credential_finalization: m.credential_finalization.0,
            dpop_binding: m.dpop_binding.0,
        },
        json!({
            "login_id": HANDSHAKE_ID,
            "credential_finalization": m.credential_finalization.1,
            "dpop_binding": m.dpop_binding.1,
        }),
    );
}

#[test]
fn step_up_finish_response() {
    pin(
        &step_up::FinishResponse {
            access: Access {
                access_token: from(json!("a.b.c")),
                access_expires_in: 900,
            },
            auth_time: 1_700_000_000,
        },
        json!({"access_token": "a.b.c", "access_expires_in": 900, "auth_time": 1_700_000_000u64}),
    );
}

#[test]
fn token_request() {
    pin(
        &TokenRequest {
            refresh_token: from::<RefreshToken>(json!(REFRESH)),
        },
        json!({"refresh_token": REFRESH}),
    );
}

#[test]
fn session() {
    pin(&sample_session(), session_json());
}

#[test]
fn devices() {
    let listed = json!({
        "devices": [
            {
                "id": NIL,
                "name": "Pixel 9",
                "platform": "android",
                "app_version": "2.3.0",
                "created_at": 1_600_000_000u64,
                "last_seen_at": 1_700_000_000u64,
                "auth_time": 1_700_000_100u64,
                "logged_in": true,
            },
            {
                "id": NIL,
                "name": "Pixel 9",
                "platform": "android",
                "app_version": "2.3.0",
                "created_at": 1_600_000_000u64,
                "last_seen_at": 1_700_000_000u64,
                "auth_time": null,
                "logged_in": false,
            },
        ],
    });
    pin(&from::<Devices>(listed.clone()), listed);
}

#[test]
fn server_info() {
    pin(
        &ServerInfo {
            version: "0.1.0".into(),
            public_url: "https://keygo.example".into(),
            allow_signups: true,
        },
        json!({"version": "0.1.0", "public_url": "https://keygo.example", "allow_signups": true}),
    );
}
