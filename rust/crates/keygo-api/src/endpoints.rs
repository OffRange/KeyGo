use crate::endpoint::{Auth, Endpoint, Method};
use crate::{auth, devices, meta};
use keygo_core::DeviceId;
use std::borrow::Cow;
use uuid::Uuid;

pub struct Meta;

impl Endpoint for Meta {
    type Request = ();
    type Response = meta::ServerInfo;
    const METHOD: Method = Method::Get;
    const AUTH: Auth = Auth::None;

    fn path(&self) -> Cow<'static, str> {
        Cow::Borrowed("/api/v1/meta")
    }
}

pub struct RegisterStart;

impl Endpoint for RegisterStart {
    type Request = auth::register::StartRequest;
    type Response = auth::register::StartResponse;
    const METHOD: Method = Method::Post;
    const AUTH: Auth = Auth::None;

    fn path(&self) -> Cow<'static, str> {
        Cow::Borrowed("/api/v1/auth/register/start")
    }
}

pub struct RegisterFinish;

impl Endpoint for RegisterFinish {
    type Request = auth::register::FinishRequest;
    type Response = auth::register::FinishResponse;
    const METHOD: Method = Method::Post;
    const AUTH: Auth = Auth::Proof;

    fn path(&self) -> Cow<'static, str> {
        Cow::Borrowed("/api/v1/auth/register/finish")
    }
}

pub struct LoginStart;

impl Endpoint for LoginStart {
    type Request = auth::login::StartRequest;
    type Response = auth::handshake::StartResponse;
    const METHOD: Method = Method::Post;
    const AUTH: Auth = Auth::None;

    fn path(&self) -> Cow<'static, str> {
        Cow::Borrowed("/api/v1/auth/login/start")
    }
}

pub struct LoginFinish;

impl Endpoint for LoginFinish {
    type Request = auth::login::FinishRequest;
    type Response = auth::login::FinishResponse;
    const METHOD: Method = Method::Post;
    const AUTH: Auth = Auth::Proof;

    fn path(&self) -> Cow<'static, str> {
        Cow::Borrowed("/api/v1/auth/login/finish")
    }
}

pub struct StepUpStart;

impl Endpoint for StepUpStart {
    type Request = auth::step_up::StartRequest;
    type Response = auth::handshake::StartResponse;
    const METHOD: Method = Method::Post;
    const AUTH: Auth = Auth::AccessToken;

    fn path(&self) -> Cow<'static, str> {
        Cow::Borrowed("/api/v1/auth/step-up/start")
    }
}

pub struct StepUpFinish;

impl Endpoint for StepUpFinish {
    type Request = auth::step_up::FinishRequest;
    type Response = auth::step_up::FinishResponse;
    const METHOD: Method = Method::Post;
    const AUTH: Auth = Auth::AccessToken;

    fn path(&self) -> Cow<'static, str> {
        Cow::Borrowed("/api/v1/auth/step-up/finish")
    }
}

pub struct Token;

impl Endpoint for Token {
    type Request = auth::token::TokenRequest;
    type Response = auth::Session;
    const METHOD: Method = Method::Post;
    const AUTH: Auth = Auth::Proof;

    fn path(&self) -> Cow<'static, str> {
        Cow::Borrowed("/api/v1/auth/token")
    }
}

pub struct Logout;

impl Endpoint for Logout {
    type Request = ();
    type Response = ();
    const METHOD: Method = Method::Post;
    const AUTH: Auth = Auth::AccessToken;

    fn path(&self) -> Cow<'static, str> {
        Cow::Borrowed("/api/v1/auth/logout")
    }
}

pub struct ListDevices;

impl Endpoint for ListDevices {
    type Request = ();
    type Response = devices::Devices;
    const METHOD: Method = Method::Get;
    const AUTH: Auth = Auth::AccessToken;

    fn path(&self) -> Cow<'static, str> {
        Cow::Borrowed("/api/v1/devices")
    }
}

pub struct DeleteDevice {
    pub id: DeviceId,
}

impl Endpoint for DeleteDevice {
    type Request = ();
    type Response = ();
    const METHOD: Method = Method::Delete;
    const AUTH: Auth = Auth::AccessToken;

    fn path(&self) -> Cow<'static, str> {
        Cow::Owned(format!("/api/v1/devices/{}", self.id.as_uuid()))
    }
}

/// An endpoint reduced to what a router or a test needs to know about it.
#[derive(Debug)]
pub struct Route {
    pub method: Method,
    pub path: String,
    pub auth: Auth,
}

fn route<E: Endpoint>(endpoint: E) -> Route {
    Route {
        method: E::METHOD,
        path: endpoint.path().into_owned(),
        auth: E::AUTH,
    }
}

pub fn all_routes() -> Vec<Route> {
    vec![
        route(Meta),
        route(RegisterStart),
        route(RegisterFinish),
        route(LoginStart),
        route(LoginFinish),
        route(StepUpStart),
        route(StepUpFinish),
        route(Token),
        route(Logout),
        route(ListDevices),
        route(DeleteDevice {
            id: Uuid::nil().into(),
        }),
    ]
}
