use crate::ark_session::ArkSession;
use async_trait::async_trait;
use keygo_api::domain::{AppVersion, Device, DeviceInfo, DeviceName, Platform};
use keygo_api::meta::ServerInfo;
use keygo_client::crypto::{DeviceId, UserId};
use keygo_client::sync::{
    Account as CoreAccount, DpopKey as CoreDpopKey, Header, HttpRequest as CoreHttpRequest,
    HttpResponse as CoreHttpResponse, SignError as CoreSignError,
    StoredSession as CoreStoredSession, SyncClient as CoreSyncClient, SyncError as CoreSyncError,
    SyncResult, TokenStore as CoreTokenStore, TokenStoreError as CoreTokenStoreError,
    Transport as CoreTransport, TransportError as CoreTransportError,
};
use std::fmt::Display;
use std::sync::Arc;
use url::Url;
use zeroize::Zeroizing;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum SyncError {
    #[error("invalid credentials")]
    InvalidCredentials,
    #[error("invalid username")]
    InvalidUsername,
    #[error("username taken")]
    UsernameTaken,
    #[error("invite required")]
    InviteRequired,
    #[error("invite invalid")]
    InviteInvalid,
    #[error("fresh authentication required")]
    FreshAuthRequired,
    /// `reason` is the wire value: `replaced`, `reuse_detected` or `logout`.
    #[error("signed out")]
    SignedOut { reason: Option<String> },
    #[error("rate limited")]
    RateLimited,
    #[error("network error: {reason}")]
    Network { reason: String },
    #[error("server error {code}: {reason}")]
    Server { code: String, reason: String },
    #[error("client error: {reason}")]
    Client { reason: String },
}

impl From<CoreSyncError> for SyncError {
    fn from(value: CoreSyncError) -> Self {
        match value {
            CoreSyncError::InvalidCredentials => Self::InvalidCredentials,
            CoreSyncError::InvalidUsername => Self::InvalidUsername,
            CoreSyncError::UsernameTaken => Self::UsernameTaken,
            CoreSyncError::InviteRequired => Self::InviteRequired,
            CoreSyncError::InviteInvalid => Self::InviteInvalid,
            CoreSyncError::FreshAuthRequired => Self::FreshAuthRequired,
            CoreSyncError::SignedOut { reason } => Self::SignedOut {
                reason: reason.map(|r| r.as_str().to_owned()),
            },
            CoreSyncError::RateLimited => Self::RateLimited,
            CoreSyncError::Network { message } => Self::Network { reason: message },
            CoreSyncError::Server { code, message } => Self::Server {
                code,
                reason: message,
            },
            CoreSyncError::Client { message } => Self::Client { reason: message },
        }
    }
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum TransportError {
    #[error("{reason}")]
    Failed { reason: String },
}

impl From<uniffi::UnexpectedUniFFICallbackError> for TransportError {
    fn from(error: uniffi::UnexpectedUniFFICallbackError) -> Self {
        Self::Failed {
            reason: error.reason,
        }
    }
}

impl From<TransportError> for CoreTransportError {
    fn from(error: TransportError) -> Self {
        let TransportError::Failed { reason } = error;
        Self { message: reason }
    }
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum SignError {
    #[error("{reason}")]
    Failed { reason: String },
}

impl From<uniffi::UnexpectedUniFFICallbackError> for SignError {
    fn from(error: uniffi::UnexpectedUniFFICallbackError) -> Self {
        Self::Failed {
            reason: error.reason,
        }
    }
}

impl From<SignError> for CoreSignError {
    fn from(error: SignError) -> Self {
        let SignError::Failed { reason } = error;
        Self { message: reason }
    }
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum TokenStoreError {
    #[error("{reason}")]
    Failed { reason: String },
}

impl From<uniffi::UnexpectedUniFFICallbackError> for TokenStoreError {
    fn from(error: uniffi::UnexpectedUniFFICallbackError) -> Self {
        Self::Failed {
            reason: error.reason,
        }
    }
}

impl From<TokenStoreError> for CoreTokenStoreError {
    fn from(error: TokenStoreError) -> Self {
        let TokenStoreError::Failed { reason } = error;
        Self { message: reason }
    }
}

#[uniffi::remote(Record)]
pub struct Header {
    pub name: String,
    pub value: String,
}

#[derive(uniffi::Record)]
pub struct HttpRequest {
    pub method: String,
    pub url: String,
    pub headers: Vec<Header>,
    pub body: Vec<u8>,
}

impl From<CoreHttpRequest> for HttpRequest {
    fn from(request: CoreHttpRequest) -> Self {
        Self {
            method: request.method.as_str().to_owned(),
            url: request.url,
            headers: request.headers,
            body: request.body,
        }
    }
}

#[derive(uniffi::Record)]
pub struct HttpResponse {
    pub status: u16,
    pub headers: Vec<Header>,
    pub body: Vec<u8>,
}

impl From<HttpResponse> for CoreHttpResponse {
    fn from(response: HttpResponse) -> Self {
        Self {
            status: response.status,
            headers: response.headers,
            body: response.body,
        }
    }
}

#[derive(uniffi::Record)]
pub struct StoredSession {
    pub device_id: DeviceId,
    pub refresh_token: String,
    pub refresh_expires_at: u64,
}

impl From<&CoreStoredSession> for StoredSession {
    fn from(session: &CoreStoredSession) -> Self {
        Self {
            device_id: session.device_id,
            refresh_token: session.refresh_token.expose_encoded().to_string(),
            refresh_expires_at: session.refresh_expires_at,
        }
    }
}

#[derive(uniffi::Record)]
pub struct DeviceDetails {
    pub name: String,
    pub app_version: String,
}

impl DeviceDetails {
    fn into_core(self) -> Result<DeviceInfo, SyncError> {
        Ok(DeviceInfo {
            name: DeviceName::parse(&self.name).map_err(invalid_device)?,
            platform: Platform::Android,
            app_version: AppVersion::parse(&self.app_version).map_err(invalid_device)?,
        })
    }
}

fn invalid_device(error: impl Display) -> SyncError {
    SyncError::Client {
        reason: format!("invalid device details: {error}"),
    }
}

#[derive(uniffi::Record)]
pub struct SyncDevice {
    pub id: DeviceId,
    pub name: String,
    pub platform: String,
    pub app_version: String,
    pub created_at: u64,
    pub last_seen_at: u64,
    pub auth_time: Option<u64>,
    pub logged_in: bool,
}

impl From<Device> for SyncDevice {
    fn from(device: Device) -> Self {
        Self {
            id: device.id,
            name: device.info.name.as_str().to_owned(),
            platform: device.info.platform.as_str().to_owned(),
            app_version: device.info.app_version.as_str().to_owned(),
            created_at: device.created_at,
            last_seen_at: device.last_seen_at,
            auth_time: device.auth_time,
            logged_in: device.logged_in,
        }
    }
}

#[uniffi::remote(Record)]
pub struct ServerInfo {
    pub version: String,
    pub public_url: String,
    pub allow_signups: bool,
}

#[derive(uniffi::Record)]
pub struct Account {
    pub user_id: UserId,
    pub username: String,
    pub device_id: DeviceId,
}

impl From<CoreAccount> for Account {
    fn from(account: CoreAccount) -> Self {
        Self {
            user_id: account.user_id,
            username: account.username.as_str().to_owned(),
            device_id: account.device_id,
        }
    }
}

/// Sends one HTTP request. The host owns TLS, timeouts and connection pooling; it must not retry,
/// follow auth challenges or log bodies.
#[uniffi::export(with_foreign)]
#[async_trait]
pub trait HttpTransport: Send + Sync {
    async fn send(&self, request: HttpRequest) -> Result<HttpResponse, TransportError>;
}

/// A hardware-backed P-256 key held by the host.
#[uniffi::export(with_foreign)]
pub trait DpopKey: Send + Sync {
    /// The 65-byte uncompressed P-256 point.
    fn public_key(&self) -> Result<Vec<u8>, SignError>;
    /// SHA256withECDSA over `message`, DER encoded.
    fn sign(&self, message: Vec<u8>) -> Result<Vec<u8>, SignError>;
}

/// Durable home of the refresh token. The host decides how it is protected.
///
/// `save` runs while the client holds its auth lock, so it must return in bounded time: one that
/// never returns blocks every later call, including logout. A refused `save` is repeated on later
/// calls.
#[uniffi::export(with_foreign)]
#[async_trait]
pub trait TokenStore: Send + Sync {
    async fn load(&self) -> Result<Option<StoredSession>, TokenStoreError>;
    async fn save(&self, session: StoredSession) -> Result<(), TokenStoreError>;
    async fn clear(&self) -> Result<(), TokenStoreError>;
}

struct TransportAdapter(Arc<dyn HttpTransport>);

#[async_trait]
impl CoreTransport for TransportAdapter {
    async fn send(&self, request: CoreHttpRequest) -> Result<CoreHttpResponse, CoreTransportError> {
        Ok(self.0.send(request.into()).await?.into())
    }
}

struct KeyAdapter(Arc<dyn DpopKey>);

impl CoreDpopKey for KeyAdapter {
    fn public_key(&self) -> Result<Vec<u8>, CoreSignError> {
        Ok(self.0.public_key()?)
    }

    fn sign(&self, message: Vec<u8>) -> Result<Vec<u8>, CoreSignError> {
        Ok(self.0.sign(message)?)
    }
}

struct StoreAdapter(Arc<dyn TokenStore>);

#[async_trait]
impl CoreTokenStore for StoreAdapter {
    /// A token that no longer parses counts as no session, so the user just signs in again.
    async fn load(&self) -> Result<Option<CoreStoredSession>, CoreTokenStoreError> {
        let Some(stored) = self.0.load().await? else {
            return Ok(None);
        };
        let refresh_token = Zeroizing::new(stored.refresh_token);
        Ok(refresh_token
            .parse()
            .ok()
            .map(|refresh_token| CoreStoredSession {
                device_id: stored.device_id,
                refresh_token,
                refresh_expires_at: stored.refresh_expires_at,
            }))
    }

    async fn save(&self, session: &CoreStoredSession) -> Result<(), CoreTokenStoreError> {
        Ok(self.0.save(session.into()).await?)
    }

    async fn clear(&self) -> Result<(), CoreTokenStoreError> {
        Ok(self.0.clear().await?)
    }
}

/// Runs `call` as its own task and waits for it. Dropping the returned future only stops the
/// waiting, and a panic in the task ends at the `JoinError` instead of crossing the FFI.
async fn detached<T, C>(call: C) -> Result<T, SyncError>
where
    T: Send + 'static,
    C: Future<Output = SyncResult<T>> + Send + 'static,
{
    tokio::spawn(call)
        .await
        .map_err(|_| SyncError::Client {
            reason: "the sync task did not finish".into(),
        })?
        .map_err(Into::into)
}

/// The sync client, over the host's HTTP stack, DPoP key and token store.
///
/// Every call runs as its own task, so cancelling the coroutine waiting for one leaves it running.
/// Refresh tokens rotate with no grace window: abandoning a refresh the server has committed would
/// lose the new token, and the next refresh would revoke the session as `reuse_detected`.
#[derive(uniffi::Object)]
pub struct SyncClient {
    inner: Arc<CoreSyncClient>,
}

#[uniffi::export(async_runtime = "tokio")]
impl SyncClient {
    #[uniffi::constructor]
    pub fn new(
        base_url: String,
        transport: Arc<dyn HttpTransport>,
        dpop_key: Arc<dyn DpopKey>,
        tokens: Arc<dyn TokenStore>,
    ) -> Result<Arc<Self>, SyncError> {
        let base_url = Url::parse(&base_url).map_err(|e| SyncError::Client {
            reason: format!("invalid server url: {e}"),
        })?;
        let inner = CoreSyncClient::new(
            base_url,
            Arc::new(TransportAdapter(transport)),
            Arc::new(KeyAdapter(dpop_key)),
            Arc::new(StoreAdapter(tokens)),
        )?;
        Ok(Arc::new(Self {
            inner: Arc::new(inner),
        }))
    }

    pub async fn meta(&self) -> Result<ServerInfo, SyncError> {
        let client = self.inner.clone();
        detached(async move { client.meta().await }).await
    }

    pub async fn register(
        &self,
        ark: Arc<ArkSession>,
        user_id: UserId,
        username: String,
        password: String,
        invite: Option<String>,
        device: DeviceDetails,
    ) -> Result<Account, SyncError> {
        let password = Zeroizing::new(password);
        let device = device.into_core()?;
        let client = self.inner.clone();
        let account = detached(async move {
            client
                .register(
                    &ark.session,
                    user_id,
                    &username,
                    &password,
                    invite.as_deref(),
                    device,
                )
                .await
        })
        .await?;
        Ok(account.into())
    }

    pub async fn login(
        &self,
        ark: Arc<ArkSession>,
        username: String,
        password: String,
        device: DeviceDetails,
    ) -> Result<Account, SyncError> {
        let password = Zeroizing::new(password);
        let device = device.into_core()?;
        let client = self.inner.clone();
        let account = detached(async move {
            client
                .login(&ark.session, &username, &password, device)
                .await
        })
        .await?;
        Ok(account.into())
    }

    pub async fn step_up(&self, password: String) -> Result<(), SyncError> {
        let password = Zeroizing::new(password);
        let client = self.inner.clone();
        detached(async move { client.step_up(&password).await }).await
    }

    pub async fn devices(&self) -> Result<Vec<SyncDevice>, SyncError> {
        let client = self.inner.clone();
        let devices = detached(async move { client.devices().await }).await?;
        Ok(devices.into_iter().map(SyncDevice::from).collect())
    }

    pub async fn delete_device(&self, id: DeviceId) -> Result<(), SyncError> {
        let client = self.inner.clone();
        detached(async move { client.delete_device(id).await }).await
    }

    pub async fn logout(&self) -> Result<(), SyncError> {
        let client = self.inner.clone();
        detached(async move { client.logout().await }).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use keygo_api::Method;
    use keygo_api::domain::RevokeReason;
    use keygo_client::sync::testing::FakeDpopKey;
    use std::sync::Mutex;
    use std::time::Duration;
    use tokio::sync::Notify;
    use tokio::time::timeout;

    /// Decodes to 32 bytes of `0xff`.
    const STORED_TOKEN: &str = "kgr___________________________________________8";
    /// Decodes to 32 bytes of zero: what the server rotates [`STORED_TOKEN`] to.
    const ROTATED_TOKEN: &str = "kgr_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    const ROTATED_SESSION: &str = r#"{"device_id":"00000000-0000-0000-0000-000000000001","access_token":"a.b.c","access_expires_in":300,"refresh_token":"kgr_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","refresh_expires_in":86400}"#;

    #[derive(Default)]
    struct MemoryStore {
        session: Mutex<Option<StoredSession>>,
        saved: Notify,
    }

    impl MemoryStore {
        fn holding(refresh_token: &str) -> Arc<Self> {
            Arc::new(Self {
                session: Mutex::new(Some(StoredSession {
                    device_id: DeviceId::random(),
                    refresh_token: refresh_token.into(),
                    refresh_expires_at: u64::MAX,
                })),
                saved: Notify::new(),
            })
        }

        fn refresh_token(&self) -> Option<String> {
            let session = self.session.lock().unwrap();
            session.as_ref().map(|s| s.refresh_token.clone())
        }
    }

    #[async_trait]
    impl TokenStore for MemoryStore {
        async fn load(&self) -> Result<Option<StoredSession>, TokenStoreError> {
            let session = self.session.lock().unwrap();
            Ok(session.as_ref().map(|s| StoredSession {
                device_id: s.device_id,
                refresh_token: s.refresh_token.clone(),
                refresh_expires_at: s.refresh_expires_at,
            }))
        }

        async fn save(&self, session: StoredSession) -> Result<(), TokenStoreError> {
            *self.session.lock().unwrap() = Some(session);
            self.saved.notify_one();
            Ok(())
        }

        async fn clear(&self) -> Result<(), TokenStoreError> {
            *self.session.lock().unwrap() = None;
            Ok(())
        }
    }

    struct PanickingStore;

    #[async_trait]
    impl TokenStore for PanickingStore {
        async fn load(&self) -> Result<Option<StoredSession>, TokenStoreError> {
            panic!("the store blew up")
        }

        async fn save(&self, _: StoredSession) -> Result<(), TokenStoreError> {
            unreachable!()
        }

        async fn clear(&self) -> Result<(), TokenStoreError> {
            unreachable!()
        }
    }

    #[derive(Default)]
    struct RecordingTransport {
        methods: Mutex<Vec<String>>,
    }

    #[async_trait]
    impl HttpTransport for RecordingTransport {
        async fn send(&self, request: HttpRequest) -> Result<HttpResponse, TransportError> {
            self.methods.lock().unwrap().push(request.method);
            Ok(HttpResponse {
                status: 204,
                headers: Vec::new(),
                body: Vec::new(),
            })
        }
    }

    /// Announces each request and holds it until released, then answers with a rotated session.
    #[derive(Default)]
    struct GatedTransport {
        reached: Notify,
        release: Notify,
    }

    #[async_trait]
    impl HttpTransport for GatedTransport {
        async fn send(&self, _: HttpRequest) -> Result<HttpResponse, TransportError> {
            self.reached.notify_one();
            self.release.notified().await;
            Ok(HttpResponse {
                status: 200,
                headers: Vec::new(),
                body: ROTATED_SESSION.as_bytes().to_vec(),
            })
        }
    }

    struct TestKey(FakeDpopKey);

    impl DpopKey for TestKey {
        fn public_key(&self) -> Result<Vec<u8>, SignError> {
            CoreDpopKey::public_key(&self.0).map_err(|e| SignError::Failed { reason: e.message })
        }

        fn sign(&self, message: Vec<u8>) -> Result<Vec<u8>, SignError> {
            CoreDpopKey::sign(&self.0, message).map_err(|e| SignError::Failed { reason: e.message })
        }
    }

    struct ThrowingKey;

    impl DpopKey for ThrowingKey {
        fn public_key(&self) -> Result<Vec<u8>, SignError> {
            Err(SignError::Failed {
                reason: "the keystore is locked".into(),
            })
        }

        fn sign(&self, _: Vec<u8>) -> Result<Vec<u8>, SignError> {
            unreachable!()
        }
    }

    fn client(transport: Arc<dyn HttpTransport>, tokens: Arc<dyn TokenStore>) -> Arc<SyncClient> {
        SyncClient::new(
            "https://kg.example".into(),
            transport,
            Arc::new(TestKey(FakeDpopKey::new())),
            tokens,
        )
        .unwrap()
    }

    #[tokio::test]
    async fn corrupt_refresh_token_loads_as_no_session() {
        let adapter = StoreAdapter(MemoryStore::holding("garbage"));

        assert!(adapter.load().await.unwrap().is_none());
    }

    #[tokio::test]
    async fn stored_session_round_trips_through_the_adapter() {
        let adapter = StoreAdapter(Arc::new(MemoryStore::default()));
        let device_id = DeviceId::random();

        adapter
            .save(&CoreStoredSession {
                device_id,
                refresh_token: STORED_TOKEN.parse().unwrap(),
                refresh_expires_at: 99,
            })
            .await
            .unwrap();
        let loaded = adapter.load().await.unwrap().unwrap();

        assert_eq!(loaded.refresh_token.expose_encoded().as_str(), STORED_TOKEN);
        assert_eq!(loaded.device_id, device_id);
        assert_eq!(loaded.refresh_expires_at, 99);
    }

    #[test]
    fn signed_out_carries_the_wire_reason() {
        let error = SyncError::from(CoreSyncError::SignedOut {
            reason: Some(RevokeReason::ReuseDetected),
        });

        assert!(matches!(
            error,
            SyncError::SignedOut { reason } if reason.as_deref() == Some("reuse_detected"),
        ));
    }

    #[test]
    fn device_details_reject_control_characters() {
        let device = DeviceDetails {
            name: "a\u{202e}b".into(),
            app_version: "2.3.0".into(),
        };

        assert!(matches!(device.into_core(), Err(SyncError::Client { .. }),));
    }

    #[tokio::test]
    async fn transport_adapter_passes_method_as_text() {
        let transport = Arc::new(RecordingTransport::default());
        let adapter = TransportAdapter(transport.clone());

        adapter
            .send(CoreHttpRequest {
                method: Method::Delete,
                url: "https://kg.example/api/v1/devices/1".into(),
                headers: Vec::new(),
                body: Vec::new(),
            })
            .await
            .unwrap();

        assert_eq!(*transport.methods.lock().unwrap(), ["DELETE"]);
    }

    /// A Kotlin coroutine that is cancelled drops the future. The rotated refresh token must still
    /// reach the store, because rotation has no grace window and losing it signs the user out.
    #[tokio::test]
    async fn a_dropped_call_still_finishes() {
        let transport = Arc::new(GatedTransport::default());
        let store = MemoryStore::holding(STORED_TOKEN);
        let client = client(transport.clone(), store.clone());

        let mut call = Box::pin(client.devices());
        tokio::select! {
            _ = &mut call => panic!("the call finished before the transport answered"),
            () = transport.reached.notified() => {},
        }
        drop(call);
        transport.release.notify_one();

        timeout(Duration::from_secs(5), store.saved.notified())
            .await
            .expect("the dropped call never saved the rotated token");
        assert_eq!(store.refresh_token().as_deref(), Some(ROTATED_TOKEN));
    }

    #[tokio::test]
    async fn a_panicking_call_is_a_client_error() {
        let client = client(
            Arc::new(RecordingTransport::default()),
            Arc::new(PanickingStore),
        );

        assert!(matches!(
            client.devices().await,
            Err(SyncError::Client { .. }),
        ));
    }

    #[test]
    fn a_throwing_public_key_is_a_client_error() {
        let result = SyncClient::new(
            "https://kg.example".into(),
            Arc::new(RecordingTransport::default()),
            Arc::new(ThrowingKey),
            Arc::new(MemoryStore::default()),
        );

        assert!(matches!(result, Err(SyncError::Client { .. })));
    }
}
