use super::error::SyncResult;
use super::signer::{DpopKey, ForeignSigner};
use super::state::{AuthState, Clock, SystemClock};
use super::tokens::TokenStore;
use super::transport::Transport;
use keygo_api::domain::Device;
use keygo_api::endpoints::{DeleteDevice, ListDevices, Logout, Meta};
use keygo_api::meta::ServerInfo;
use keygo_core::DeviceId;
use keygo_dpop::DpopProver;
use std::sync::Arc;
use tokio::sync::Mutex;
use url::Url;

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

    /// Ends the session here even if the server cannot be told, and reports whether it was.
    pub async fn logout(&self) -> SyncResult<()> {
        let sent = self.call(&Logout, &()).await;
        let mut state = self.auth.lock().await;
        self.end_session(&mut state).await;
        sent
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
