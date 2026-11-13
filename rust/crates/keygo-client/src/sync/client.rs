use super::error::{SyncError, SyncResult};
use super::pipeline::store_failure;
use super::signer::SharedSigner;
use super::state::{AuthState, Clock, SystemClock};
use super::tokens::TokenStore;
use super::transport::Transport;
use crate::ark_session::{ArkSession, ArkSessionError};
use keygo_api::auth::{Session, login, register, step_up};
use keygo_api::domain::{Device, DeviceClaim, DeviceInfo, KeyBundle, Username};
use keygo_api::endpoints::{
    DeleteDevice, ListDevices, LoginFinish, LoginStart, Logout, Meta, RegisterFinish,
    RegisterStart, StepUpFinish, StepUpStart,
};
use keygo_api::meta::ServerInfo;
use keygo_core::{CryptoError, DeviceId, UserId};
use keygo_dpop::DpopProver;
use keygo_opaque::OpaqueError;
use keygo_opaque::client::{ClientLogin, ClientRegistration};
use std::fmt::Debug;
use std::sync::Arc;
use tokio::sync::Mutex;
use url::Url;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Account {
    pub user_id: UserId,
    pub username: Username,
    pub device_id: DeviceId,
}

pub struct SyncClient {
    pub(super) base_url: Url,
    pub(super) transport: Arc<dyn Transport>,
    pub(super) prover: DpopProver<SharedSigner>,
    pub(super) tokens: Arc<dyn TokenStore>,
    pub(super) clock: Arc<dyn Clock>,
    pub(super) auth: Mutex<AuthState>,
    /// Outside `auth` because a refresh holds that lock across the network, and it signs too.
    pub(super) nonce: std::sync::Mutex<Option<String>>,
}

fn check_base_url(url: &Url) -> SyncResult<()> {
    let is_origin = matches!(url.scheme(), "http" | "https")
        && url.has_host()
        && url.path() == "/"
        && url.query().is_none()
        && url.fragment().is_none();
    if is_origin {
        return Ok(());
    }
    Err(SyncError::Client {
        message: "the server url must be a plain http(s) origin".into(),
    })
}

impl SyncClient {
    pub fn new(
        base_url: Url,
        transport: Arc<dyn Transport>,
        signer: SharedSigner,
        tokens: Arc<dyn TokenStore>,
    ) -> SyncResult<Self> {
        check_base_url(&base_url)?;
        Ok(Self {
            base_url,
            transport,
            prover: DpopProver::new(signer),
            tokens,
            clock: Arc::new(SystemClock),
            auth: Mutex::new(AuthState::default()),
            nonce: std::sync::Mutex::default(),
        })
    }

    #[cfg(any(test, feature = "test-support"))]
    pub fn with_clock(mut self, clock: Arc<dyn Clock>) -> Self {
        self.clock = clock;
        self
    }

    pub async fn meta(&self) -> SyncResult<ServerInfo> {
        self.call(&Meta, &()).await
    }

    pub async fn devices(&self) -> SyncResult<Vec<Device>> {
        Ok(self.call(&ListDevices, &()).await?.devices)
    }

    pub async fn delete_device(&self, id: DeviceId) -> SyncResult<()> {
        self.call(&DeleteDevice { id }, &()).await
    }

    /// Proves the password again. Replaces only the access token: the refresh token stays as it is.
    pub async fn step_up(&self, password: &str) -> SyncResult<()> {
        let (login, credential_request) =
            ClientLogin::start(password.as_bytes()).map_err(opaque_failure)?;
        let started = self
            .call(&StepUpStart, &step_up::StartRequest { credential_request })
            .await?;
        let finish = login
            .finish(password.as_bytes(), started.credential_response)
            .map_err(opaque_failure)?;
        let dpop_binding = finish.session_key.dpop_binding(self.prover.jkt());

        let sent_at = self.clock.now_secs();
        let response = self
            .call(
                &StepUpFinish,
                &step_up::FinishRequest {
                    login_id: started.login_id,
                    credential_finalization: finish.finalization,
                    dpop_binding,
                },
            )
            .await?;

        self.auth
            .lock()
            .await
            .replace_access(response.access, sent_at);
        Ok(())
    }

    /// Creates an account whose server copy of the ARK is the one `ark` holds, and signs in.
    ///
    /// `user_id` must be the local account's id: the server-wrapped ARK is bound to it, and the
    /// server stores it as the account's id. `ark` cannot check this, since it holds no user id.
    /// Any session this client held is logged out once the new one exists; a failed registration
    /// leaves it as it was.
    pub async fn register(
        &self,

        ark: &ArkSession,
        user_id: UserId,
        username: String,
        password: &str,
        invite: Option<String>,
        device: DeviceInfo,
    ) -> SyncResult<Account> {
        if !ark.is_active() {
            return Err(SyncError::VaultLocked);
        }
        let (client, registration_request) =
            ClientRegistration::start(password.as_bytes()).map_err(opaque_failure)?;

        let started = self
            .call(
                &RegisterStart,
                &register::StartRequest {
                    username,
                    user_id,
                    invite,
                    registration_request,
                },
            )
            .await?;
        let finish = client
            .finish(password.as_bytes(), started.registration_response)
            .map_err(|error| SyncError::invalid_response(error.to_string()))?;

        let wrapped_ark = ark
            .wrap_for_server(&finish.export_key, user_id)
            .map_err(|error| match error {
                ArkSessionError::Locked => SyncError::VaultLocked,
                other => client_failure(other),
            })?
            .into();
        let key_bundle = KeyBundle { wrapped_ark };

        let sent_at = self.clock.now_secs();
        let response = self
            .call(
                &RegisterFinish,
                &register::FinishRequest {
                    registration_id: started.registration_id,
                    registration_upload: finish.upload,
                    key_bundle,
                    device,
                },
            )
            .await?;

        let account = Account {
            user_id,
            username: response.username,
            device_id: response.session.device_id,
        };
        self.replace_session(response.session, sent_at, None).await;
        Ok(account)
    }

    pub async fn login(
        &self,
        ark: &ArkSession,
        username: String,
        password: &str,
        device: DeviceInfo,
    ) -> SyncResult<Account> {
        let (client, credential_request) =
            ClientLogin::start(password.as_bytes()).map_err(opaque_failure)?;
        let started = self
            .call(
                &LoginStart,
                &login::StartRequest {
                    username,
                    credential_request,
                },
            )
            .await?;
        let finish = client
            .finish(password.as_bytes(), started.credential_response)
            .map_err(opaque_failure)?;
        let dpop_binding = finish.session_key.dpop_binding(self.prover.jkt());

        let sent_at = self.clock.now_secs();
        let response = self
            .call(
                &LoginFinish,
                &login::FinishRequest {
                    login_id: started.login_id,
                    credential_finalization: finish.finalization,
                    device: DeviceClaim {
                        id: None,
                        info: device,
                    },
                    dpop_binding,
                },
            )
            .await?;

        let account = Account {
            user_id: response.user_id,
            username: response.username,
            device_id: response.session.device_id,
        };

        // We dont catch the error early, because the server already created the session and the
        // device. To revoke that session from the server, we adopt the session so logout is available.
        let unlocked = ark.unlock_with_server(
            &finish.export_key,
            &response.key_bundle.into_wrapped_ark(),
            response.user_id,
        );
        self.adopt_session(response.session, sent_at).await;

        if let Err(error) = unlocked {
            let _ = self.logout().await;
            return Err(unlock_failure(error));
        }

        Ok(account)
    }

    /// Adopts `session` in place of the one this client held. If the server reattached `claimed`,
    /// it already ended that device's old session. Otherwise the old one is logged out first, so
    /// its refresh token does not stay valid. That logout is best effort: the new session is live
    /// on the server either way, and dropping it would only leave a second token nobody holds.
    async fn replace_session(&self, session: Session, sent_at: u64, claimed: Option<DeviceId>) {
        if claimed != Some(session.device_id) {
            let _ = self.logout().await;
        }
        self.adopt_session(session, sent_at).await;
    }

    /// Always ends the session locally. Returns the server's result, unless the server was not told
    /// and the store could not be cleared: a token the server may still accept is then left on disk.
    pub async fn logout(&self) -> SyncResult<()> {
        let sent = self.call(&Logout, &()).await;
        let mut state = self.auth.lock().await;
        let cleared = self.end_session(&mut state).await;
        match (sent, cleared) {
            (Err(_), Err(error)) => Err(store_failure(error)),
            (sent, _) => sent,
        }
    }
}

fn client_failure(error: impl std::fmt::Display) -> SyncError {
    SyncError::Client {
        message: error.to_string(),
    }
}

/// The server's copy of the ARK not opening is the server's fault: OPAQUE already proved the
/// password, and the export key follows from it.
fn unlock_failure(error: ArkSessionError) -> SyncError {
    match error {
        ArkSessionError::AccountMismatch => SyncError::AccountMismatch,
        ArkSessionError::KeyWrap(
            error @ (CryptoError::KeyUnwrapFailed | CryptoError::DecryptionFailed),
        ) => SyncError::invalid_response(format!("the server's key bundle: {error}")),
        other => client_failure(other),
    }
}

fn opaque_failure(error: OpaqueError) -> SyncError {
    match error {
        OpaqueError::InvalidLogin | OpaqueError::EmptyPassword => SyncError::InvalidCredentials,
        other => client_failure(other),
    }
}

#[cfg(test)]
mod tests {
    use super::super::error::SyncError;
    use super::super::testing::{error_body, fake_client, header};
    use super::super::tokens::StoredSession;
    use super::super::transport::HttpRequest;
    use serde_json::json;

    const INFO: &str = r#"{"version":"1","public_url":"https://kg.example","allow_signups":true}"#;

    #[tokio::test]
    async fn none_auth_sends_no_proof_or_token() {
        let (client, transport) = fake_client();
        transport.push(200, &[], INFO.as_bytes());

        client.meta().await.unwrap();

        let requests = transport.requests();
        assert_eq!(requests.len(), 1);
        assert_eq!(header(&requests[0], "dpop"), None);
        assert_eq!(header(&requests[0], "authorization"), None);
        assert_eq!(requests[0].method, keygo_api::Method::Get);
        assert_eq!(requests[0].url, "https://kg.example/api/v1/meta");
    }

    #[tokio::test]
    async fn unit_request_has_no_body_or_content_type() {
        let (client, transport) = fake_client();
        transport.push(200, &[], INFO.as_bytes());

        client.meta().await.unwrap();

        let request = &transport.requests()[0];
        assert!(request.body.is_empty());
        assert_eq!(header(request, "content-type"), None);
    }

    #[tokio::test]
    async fn error_codes_map_to_sync_errors() {
        for (code, expected) in [
            ("invalid_credentials", SyncError::InvalidCredentials),
            ("invalid_username", SyncError::InvalidUsername),
            ("username_taken", SyncError::UsernameTaken),
            ("invite_required", SyncError::InviteRequired),
            ("invite_invalid", SyncError::InviteInvalid),
            ("fresh_auth_required", SyncError::FreshAuthRequired),
            ("rate_limited", SyncError::RateLimited),
        ] {
            let (client, transport) = fake_client();
            transport.push(400, &[], &error_body(code));

            assert_eq!(client.meta().await.unwrap_err(), expected, "{code}");
        }
    }

    #[tokio::test]
    async fn rate_limited_is_not_retried() {
        let (client, transport) = fake_client();
        transport.push(
            429,
            &[],
            br#"{"code":"rate_limited","message":"slow down"}"#,
        );

        assert_eq!(client.meta().await.unwrap_err(), SyncError::RateLimited);
        assert_eq!(transport.requests().len(), 1);
    }

    #[tokio::test]
    async fn unknown_code_is_a_server_error() {
        let (client, transport) = fake_client();
        transport.push(400, &[], br#"{"code":"brand_new","message":"m"}"#);

        assert_eq!(
            client.meta().await.unwrap_err(),
            SyncError::Server {
                code: "unknown".into(),
                message: "m".into(),
            },
        );
    }

    #[tokio::test]
    async fn html_200_is_an_invalid_response() {
        let (client, transport) = fake_client();
        transport.push(200, &[], b"<html>");

        assert!(matches!(
            client.meta().await.unwrap_err(),
            SyncError::Server { code, .. } if code == "invalid_response",
        ));
    }

    #[tokio::test]
    async fn html_502_is_a_server_error() {
        let (client, transport) = fake_client();
        transport.push(502, &[], b"<html>");

        assert!(matches!(
            client.meta().await.unwrap_err(),
            SyncError::Server { code, .. } if code == "http_502",
        ));
    }

    mod register {
        use super::super::super::testing::{FakeSigner, client_with_store, error_body, header};
        use super::super::super::transport::HttpRequest;
        use super::super::SyncError;
        use super::{OLD_REFRESH, old_session_reply, paths, previous_session};
        use crate::ark_session::ArkSession;
        use base64::Engine;
        use base64::engine::general_purpose::URL_SAFE_NO_PAD;
        use keygo_api::domain::DeviceInfo;
        use keygo_core::UserId;
        use keygo_opaque::server::{self, ServerSetup};
        use keygo_opaque::{OpaqueError, RegistrationRequest};
        use serde_json::{Value, json};
        use std::sync::Arc;

        const REGISTRATION_ID: &str = "BwcHBwcHBwcHBwcHBwcHBw";
        const REFRESH: &str = "kgr_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        fn unlocked() -> (ArkSession, UserId) {
            let ark = ArkSession::new();
            let user_id = ark.create_account("local password").unwrap().user_id;
            (ark, user_id)
        }

        fn device() -> DeviceInfo {
            serde_json::from_value(json!({
                "name": "Pixel 9",
                "platform": "android",
                "app_version": "2.3.0",
            }))
            .unwrap()
        }

        fn body(request: &HttpRequest) -> Value {
            serde_json::from_slice(&request.body).unwrap()
        }

        /// What a real server answers to `register/start`.
        fn start_reply(request: &HttpRequest) -> Vec<u8> {
            let registration_request: RegistrationRequest =
                serde_json::from_value(body(request)["registration_request"].clone()).unwrap();
            let response =
                server::registration_start(&ServerSetup::generate(), registration_request, b"u")
                    .unwrap();
            serde_json::to_vec(&json!({
                "registration_id": REGISTRATION_ID,
                "registration_response": response,
                "expires_in": 300,
            }))
            .unwrap()
        }

        fn finish_reply() -> Vec<u8> {
            serde_json::to_vec(&json!({
                "username": "ada",
                "session": {
                    "device_id": "00000000-0000-0000-0000-000000000001",
                    "access_token": "a.b.c",
                    "access_expires_in": 600,
                    "refresh_token": REFRESH,
                    "refresh_expires_in": 86400,
                },
            }))
            .unwrap()
        }

        #[tokio::test]
        async fn registers_and_keeps_the_session() {
            let (client, transport, store) =
                client_with_store("https://kg.example", Arc::new(FakeSigner::new()));
            transport.push_with(200, start_reply);
            transport.push(200, &[], &finish_reply());
            let (ark, user_id) = unlocked();

            let account = client
                .register(&ark, user_id, "Ada".into(), "pw", None, device())
                .await
                .unwrap();

            assert_eq!(account.user_id, user_id);
            assert_eq!(account.username.as_str(), "ada");
            assert_eq!(store.current_refresh_token().as_deref(), Some(REFRESH));

            let requests = transport.requests();
            assert_eq!(requests.len(), 2, "a signed-out client sends no logout");
            assert_eq!(header(&requests[0], "dpop"), None);
            assert!(header(&requests[1], "dpop").is_some());

            let finish = body(&requests[1]);
            assert_eq!(finish["registration_id"], REGISTRATION_ID);
            let wrapped_ark = URL_SAFE_NO_PAD
                .decode(finish["key_bundle"]["wrapped_ark"].as_str().unwrap())
                .unwrap();
            assert_eq!(wrapped_ark.len(), 60);
        }

        #[tokio::test]
        async fn locked_vault_sends_nothing() {
            let (client, transport, _) =
                client_with_store("https://kg.example", Arc::new(FakeSigner::new()));

            let error = client
                .register(
                    &ArkSession::new(),
                    UserId::random(),
                    "ada".into(),
                    "pw",
                    None,
                    device(),
                )
                .await
                .unwrap_err();

            assert_eq!(error, SyncError::VaultLocked);
            assert!(transport.requests().is_empty());
        }

        #[tokio::test]
        async fn empty_password_sends_nothing() {
            let (client, transport, _) =
                client_with_store("https://kg.example", Arc::new(FakeSigner::new()));
            let (ark, user_id) = unlocked();

            let error = client
                .register(&ark, user_id, "ada".into(), "", None, device())
                .await
                .unwrap_err();

            assert_eq!(error, SyncError::InvalidCredentials);
            assert!(transport.requests().is_empty());
        }

        /// A server that echoes the blinded element would learn the OPRF output; the client
        /// refuses, and that is the server's fault, not a wrong password.
        #[tokio::test]
        async fn reflected_response_is_an_invalid_response() {
            let (client, transport, _) =
                client_with_store("https://kg.example", Arc::new(FakeSigner::new()));
            transport.push_with(200, |request| {
                let blinded = URL_SAFE_NO_PAD
                    .decode(body(request)["registration_request"].as_str().unwrap())
                    .unwrap();
                let reflected = [blinded, ServerSetup::generate().public_key()].concat();
                serde_json::to_vec(&json!({
                    "registration_id": REGISTRATION_ID,
                    "registration_response": URL_SAFE_NO_PAD.encode(reflected),
                    "expires_in": 300,
                }))
                .unwrap()
            });
            let (ark, user_id) = unlocked();

            let error = client
                .register(&ark, user_id, "ada".into(), "pw", None, device())
                .await
                .unwrap_err();

            assert_eq!(
                error,
                SyncError::invalid_response(OpaqueError::ReflectedValue.to_string()),
            );
            assert_eq!(transport.requests().len(), 1, "finish is never sent");
        }

        #[tokio::test]
        async fn failed_register_keeps_the_previous_session() {
            let (client, transport, store) =
                client_with_store("https://kg.example", Arc::new(FakeSigner::new()));
            store.seed(previous_session());
            transport.push(409, &[], &error_body("username_taken"));
            let (ark, user_id) = unlocked();

            let error = client
                .register(&ark, user_id, "ada".into(), "pw", None, device())
                .await
                .unwrap_err();

            assert_eq!(error, SyncError::UsernameTaken);
            assert_eq!(store.current_refresh_token().as_deref(), Some(OLD_REFRESH));
            assert_eq!(transport.requests().len(), 1, "no logout");
        }

        #[tokio::test]
        async fn previous_session_is_logged_out_once_registered() {
            let (client, transport, store) =
                client_with_store("https://kg.example", Arc::new(FakeSigner::new()));
            store.seed(previous_session());
            transport.push_with(200, start_reply);
            transport.push(200, &[], &finish_reply());
            transport.push(200, &[], &old_session_reply());
            transport.push(204, &[], b"");
            let (ark, user_id) = unlocked();

            client
                .register(&ark, user_id, "ada".into(), "pw", None, device())
                .await
                .unwrap();

            let paths = paths(&transport.requests());
            assert_eq!(
                paths,
                [
                    "/api/v1/auth/register/start",
                    "/api/v1/auth/register/finish",
                    "/api/v1/auth/token",
                    "/api/v1/auth/logout",
                ],
            );
            assert_eq!(store.current_refresh_token().as_deref(), Some(REFRESH));
        }
    }

    pub(super) const OLD_REFRESH: &str = "kgr___________________________________________8";
    const OLD_DEVICE: &str = "00000000-0000-0000-0000-00000000000d";

    /// A session of whatever account this client was signed in to before.
    pub(super) fn previous_session() -> StoredSession {
        StoredSession {
            device_id: serde_json::from_value(json!(OLD_DEVICE)).unwrap(),
            refresh_token: OLD_REFRESH.parse().unwrap(),
            refresh_expires_at: u64::MAX / 2,
        }
    }

    /// What the token endpoint answers when the previous session refreshes to log out.
    pub(super) fn old_session_reply() -> Vec<u8> {
        serde_json::to_vec(&json!({
            "device_id": OLD_DEVICE,
            "access_token": "old.access.token",
            "access_expires_in": 600,
            "refresh_token": "kgr_AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE",
            "refresh_expires_in": 86400,
        }))
        .unwrap()
    }

    pub(super) fn paths(requests: &[HttpRequest]) -> Vec<String> {
        requests
            .iter()
            .map(|r| r.url.trim_start_matches("https://kg.example").to_owned())
            .collect()
    }

    #[tokio::test]
    async fn transport_failure_is_network() {
        let (client, transport) = fake_client();
        transport.push_network_error("offline");

        assert_eq!(
            client.meta().await.unwrap_err(),
            SyncError::Network {
                message: "offline".into(),
            },
        );
    }
}
