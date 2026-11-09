use super::error::SyncError;
use super::error::SyncResult;
use super::pipeline::store_failure;
use super::signer::{DpopKey, ForeignSigner};
use super::state::{AccessState, AuthState, Clock, SystemClock};
use super::tokens::TokenStore;
use super::transport::Transport;
use crate::ark_session::ArkSession;
use keygo_api::auth::step_up::{FinishRequest, StartRequest};
use keygo_api::domain::{Device, DeviceInfo, Username};
use keygo_api::endpoints::{DeleteDevice, ListDevices, Logout, Meta, StepUpFinish, StepUpStart};
use keygo_api::meta::ServerInfo;
use keygo_core::{DeviceId, UserId};
use keygo_dpop::DpopProver;
use keygo_opaque::OpaqueError;
use keygo_opaque::client::ClientLogin;
use std::sync::Arc;
use tokio::sync::Mutex;
use url::Url;

/// Who signed in, and as which device.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Account {
    pub user_id: UserId,
    pub username: Username,
    pub device_id: DeviceId,
}

pub struct SyncClient {
    pub(super) base_url: Url,
    pub(super) transport: Arc<dyn Transport>,
    pub(super) prover: DpopProver<ForeignSigner>,
    pub(super) tokens: Arc<dyn TokenStore>,
    pub(super) clock: Arc<dyn Clock>,
    pub(super) auth: Mutex<AuthState>,
}

impl SyncClient {
    pub fn new(
        base_url: Url,
        transport: Arc<dyn Transport>,
        key: Arc<dyn DpopKey>,
        tokens: Arc<dyn TokenStore>,
    ) -> SyncResult<Self> {
        Ok(Self {
            base_url,
            transport,
            prover: DpopProver::new(ForeignSigner::new(key)?),
            tokens,
            clock: Arc::new(SystemClock),
            auth: Mutex::new(AuthState::default()),
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

    /// Proves the password again to the server, which then treats this session as freshly
    /// authenticated. Only the access token changes: the refresh token and the store are untouched.
    pub async fn step_up(&self, password: &str) -> SyncResult<()> {
        let (login, credential_request) =
            ClientLogin::start(password.as_bytes()).map_err(opaque_failure)?;
        let started = self
            .call(&StepUpStart, &StartRequest { credential_request })
            .await?;
        let finish = login
            .finish(password.as_bytes(), started.credential_response)
            .map_err(opaque_failure)?;
        let dpop_binding = finish.session_key.dpop_binding(self.prover.jkt());

        // Read before sending, so a token never looks like it lives longer than it does.
        let now = self.clock.now_secs();
        let response = self
            .call(
                &StepUpFinish,
                &FinishRequest {
                    login_id: started.login_id,
                    credential_finalization: finish.finalization,
                    dpop_binding,
                },
            )
            .await?;

        self.auth.lock().await.replace_access(AccessState {
            token: response.access.access_token,
            expires_at: now.saturating_add(response.access.access_expires_in),
        });
        Ok(())
    }

    /// Not implemented until the key bundle's wrapping is decided.
    pub async fn register(
        &self,
        _ark: &ArkSession,
        _user_id: UserId,
        _username: &str,
        _password: &str,
        _invite: Option<&str>,
        _device: DeviceInfo,
    ) -> SyncResult<Account> {
        Err(undecided())
    }

    /// Not implemented until the key bundle's wrapping is decided.
    pub async fn login(
        &self,
        _ark: &ArkSession,
        _username: &str,
        _password: &str,
        _device: DeviceInfo,
    ) -> SyncResult<Account> {
        Err(undecided())
    }

    /// Ends the session here even if the server cannot be told, and reports whether it was.
    ///
    /// When the server could not be told and the store could not be cleared either, a token the
    /// server may still accept stays on disk, so the store failure is returned instead.
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

fn undecided() -> SyncError {
    SyncError::Client {
        message: "key bundle wrapping is not decided".into(),
    }
}

fn opaque_failure(error: OpaqueError) -> SyncError {
    match error {
        OpaqueError::InvalidLogin | OpaqueError::EmptyPassword => SyncError::InvalidCredentials,
        other => SyncError::Client {
            message: other.to_string(),
        },
    }
}

#[cfg(test)]
mod tests {
    use super::super::error::SyncError;
    use super::super::signer::SignError;
    use super::super::testing::{FakeDpopKey, FakeTokenStore, FakeTransport};
    use super::*;

    const INFO: &str = r#"{"version":"1","public_url":"https://kg.example","allow_signups":true}"#;

    fn client() -> (SyncClient, Arc<FakeTransport>) {
        let transport = FakeTransport::new();
        let client = SyncClient::new(
            Url::parse("https://kg.example").unwrap(),
            transport.clone(),
            Arc::new(FakeDpopKey::new()),
            Arc::new(FakeTokenStore::new()),
        )
        .unwrap();
        (client, transport)
    }

    fn header<'a>(request: &'a super::super::HttpRequest, name: &str) -> Option<&'a str> {
        request
            .headers
            .iter()
            .find(|h| h.name == name)
            .map(|h| h.value.as_str())
    }

    #[tokio::test]
    async fn none_auth_sends_no_proof_or_token() {
        let (client, transport) = client();
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
        let (client, transport) = client();
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
            let (client, transport) = client();
            let body = format!(r#"{{"code":"{code}","message":"m"}}"#);
            transport.push(400, &[], body.as_bytes());

            assert_eq!(client.meta().await.unwrap_err(), expected, "{code}");
        }
    }

    #[tokio::test]
    async fn rate_limited_is_not_retried() {
        let (client, transport) = client();
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
        let (client, transport) = client();
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
        let (client, transport) = client();
        transport.push(200, &[], b"<html>");

        assert!(matches!(
            client.meta().await.unwrap_err(),
            SyncError::Server { code, .. } if code == "invalid_response",
        ));
    }

    #[tokio::test]
    async fn html_502_is_a_server_error() {
        let (client, transport) = client();
        transport.push(502, &[], b"<html>");

        assert!(matches!(
            client.meta().await.unwrap_err(),
            SyncError::Server { code, .. } if code == "http_502",
        ));
    }

    #[tokio::test]
    async fn transport_failure_is_network() {
        let (client, transport) = client();
        transport.push_network_error("offline");

        assert_eq!(
            client.meta().await.unwrap_err(),
            SyncError::Network {
                message: "offline".into(),
            },
        );
    }

    struct ShortKey;

    impl DpopKey for ShortKey {
        fn public_key(&self) -> Vec<u8> {
            vec![1, 2, 3]
        }

        fn sign(&self, _: Vec<u8>) -> Result<Vec<u8>, SignError> {
            unreachable!()
        }
    }

    #[test]
    fn bad_public_key_is_rejected_at_construction() {
        let result = SyncClient::new(
            Url::parse("https://kg.example").unwrap(),
            FakeTransport::new(),
            Arc::new(ShortKey),
            Arc::new(FakeTokenStore::new()),
        );

        assert!(matches!(result, Err(SyncError::Client { .. })));
    }
}
