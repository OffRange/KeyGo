use crate::endpoint::{Auth, Endpoint, Method};
use crate::{auth, devices, meta};
use keygo_core::DeviceId;
use std::borrow::Cow;
use uuid::Uuid;

macro_rules! endpoints {
    ($($name:ident: $method:ident $path:literal, $auth:ident, $request:ty => $response:ty;)+) => {
        $(
            pub struct $name;

            impl Endpoint for $name {
                type Request = $request;
                type Response = $response;
                const METHOD: Method = Method::$method;
                const AUTH: Auth = Auth::$auth;

                fn path(&self) -> Cow<'static, str> {
                    Cow::Borrowed($path)
                }
            }
        )+

        fn fixed_routes() -> Vec<Route> {
            vec![$(route($name)),+]
        }
    };
}

endpoints! {
    Meta: Get "/api/v1/meta", None, () => meta::ServerInfo;
    RegisterStart: Post "/api/v1/auth/register/start", None,
        auth::register::StartRequest => auth::register::StartResponse;
    RegisterFinish: Post "/api/v1/auth/register/finish", Proof,
        auth::register::FinishRequest => auth::register::FinishResponse;
    LoginStart: Post "/api/v1/auth/login/start", None,
        auth::login::StartRequest => auth::handshake::StartResponse;
    LoginFinish: Post "/api/v1/auth/login/finish", Proof,
        auth::login::FinishRequest => auth::login::FinishResponse;
    StepUpStart: Post "/api/v1/auth/step-up/start", AccessToken,
        auth::step_up::StartRequest => auth::handshake::StartResponse;
    StepUpFinish: Post "/api/v1/auth/step-up/finish", AccessToken,
        auth::step_up::FinishRequest => auth::step_up::FinishResponse;
    Token: Post "/api/v1/auth/token", Proof, auth::token::TokenRequest => auth::Session;
    Logout: Post "/api/v1/auth/logout", AccessToken, () => ();
    ListDevices: Get "/api/v1/devices", AccessToken, () => devices::Devices;
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
    let mut routes = fixed_routes();
    routes.push(route(DeleteDevice {
        id: Uuid::nil().into(),
    }));
    routes
}
