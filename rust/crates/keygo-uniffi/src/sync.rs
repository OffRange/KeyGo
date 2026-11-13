use crate::ark_session::ArkSession;
use async_trait::async_trait;
use keygo_api::domain::{AppVersion, Device, DeviceInfo, DeviceName, Platform};
use keygo_api::meta::ServerInfo;
use keygo_client::crypto::{DeviceId, UserId};
use keygo_client::sync::{
    Account as CoreAccount, Header, HttpRequest as CoreHttpRequest, HttpResponse,
    SignError as CoreSignError, StoredSession as CoreStoredSession, SyncClient as CoreSyncClient,
    SyncError as CoreSyncError, SyncResult, TokenStore as CoreTokenStore,
    TokenStoreError as CoreTokenStoreError, Transport as CoreTransport,
    TransportError as CoreTransportError,
};
use keygo_dpop::{DpopSigner, Signature, VerifyingKey};
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
    #[error("vault locked")]
    VaultLocked,
    /// The account signed in to holds a different ARK than the unlocked local vault.
    #[error("account mismatch")]
    AccountMismatch,
    #[error("network error: {reason}")]
    Network { reason: String },
    #[error("server error {code}: {reason}")]
    Server { code: String, reason: String },
    #[error("client error: {reason}")]
    Client { reason: String },
    #[error("{message}")]
    InvalidUrl { message: String },
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
            CoreSyncError::VaultLocked => Self::VaultLocked,
            CoreSyncError::AccountMismatch => Self::AccountMismatch,
            CoreSyncError::Network { message } => Self::Network { reason: message },
            CoreSyncError::Server { code, message } => Self::Server {
                code,
                reason: message,
            },
            CoreSyncError::Client { message } => Self::Client { reason: message },
            CoreSyncError::InvalidUrl(e) => Self::InvalidUrl {
                message: e.to_string(),
            },
        }
    }
}

macro_rules! callback_error {
    ($($name:ident => $core:ident),+ $(,)?) => {$(
        #[derive(Debug, thiserror::Error, uniffi::Error)]
        pub enum $name {
            #[error("{reason}")]
            Failed { reason: String },
        }

        impl From<uniffi::UnexpectedUniFFICallbackError> for $name {
            fn from(error: uniffi::UnexpectedUniFFICallbackError) -> Self {
                Self::Failed {
                    reason: error.reason,
                }
            }
        }

        impl From<$name> for $core {
            fn from(error: $name) -> Self {
                let $name::Failed { reason } = error;
                Self { message: reason }
            }
        }
    )+};
}

callback_error!(
    TransportError => CoreTransportError,
    SignError => CoreSignError,
    TokenStoreError => CoreTokenStoreError,
);

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

#[uniffi::remote(Record)]
pub struct HttpResponse {
    pub status: u16,
    pub headers: Vec<Header>,
    pub body: Vec<u8>,
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

/// The host owns TLS, timeouts and pooling. It must not retry, follow auth challenges or log bodies.
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

    /// SHA256withECDSA over `message`, DER encoded. Can be called from several threads at once.
    fn sign(&self, message: Vec<u8>) -> Result<Vec<u8>, SignError>;
}

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
    async fn send(&self, request: CoreHttpRequest) -> Result<HttpResponse, CoreTransportError> {
        Ok(self.0.send(request.into()).await?)
    }
}

struct ForeignSigner {
    key: Arc<dyn DpopKey>,
    verifying: VerifyingKey,
}

impl ForeignSigner {
    fn new(key: Arc<dyn DpopKey>) -> Result<Self, SyncError> {
        let public_key = key.public_key().map_err(|e| SyncError::Client {
            reason: format!("could not read the DPoP public key: {e}"),
        })?;
        let verifying =
            VerifyingKey::from_sec1_bytes(&public_key).map_err(|_| SyncError::Client {
                reason: "the DPoP public key is not a P-256 point".into(),
            })?;
        Ok(Self { key, verifying })
    }
}

impl DpopSigner for ForeignSigner {
    type Error = CoreSignError;

    fn verifying_key(&self) -> &VerifyingKey {
        &self.verifying
    }

    fn sign(&self, msg: &[u8]) -> Result<Signature, CoreSignError> {
        let der = self.key.sign(msg.to_vec())?;
        Signature::from_der(&der).map_err(|_| CoreSignError {
            message: "the DPoP signature is not DER".into(),
        })
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

#[derive(uniffi::Object)]
pub struct SyncClient {
    inner: Arc<CoreSyncClient>,
}

async fn detached<T: Send + 'static>(
    call: impl Future<Output = SyncResult<T>> + Send + 'static,
) -> Result<T, SyncError> {
    tokio::spawn(call)
        .await
        .map_err(|_| SyncError::Client {
            reason: "the sync task did not finish".into(),
        })?
        .map_err(Into::into)
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
            Arc::new(ForeignSigner::new(dpop_key)?),
            Arc::new(StoreAdapter(tokens)),
        )?;
        Ok(Arc::new(Self {
            inner: Arc::new(inner),
        }))
    }

    pub async fn meta(self: Arc<Self>) -> Result<ServerInfo, SyncError> {
        detached(async move { self.inner.meta().await }).await
    }

    pub async fn register(
        self: Arc<Self>,
        ark: Arc<ArkSession>,
        user_id: UserId,
        username: String,
        password: String,
        invite: Option<String>,
        device: DeviceDetails,
    ) -> Result<Account, SyncError> {
        let password = Zeroizing::new(password);
        let device = device.into_core()?;
        let account = detached(async move {
            self.inner
                .register(&ark.session, user_id, username, &password, invite, device)
                .await
        })
        .await?;
        Ok(account.into())
    }

    pub async fn login(
        self: Arc<Self>,
        ark: Arc<ArkSession>,
        username: String,
        password: String,
        device: DeviceDetails,
    ) -> Result<Account, SyncError> {
        let password = Zeroizing::new(password);
        let device = device.into_core()?;
        let account = detached(async move {
            self.inner
                .login(&ark.session, username, &password, device)
                .await
        })
        .await?;
        Ok(account.into())
    }

    pub async fn step_up(self: Arc<Self>, password: String) -> Result<(), SyncError> {
        let password = Zeroizing::new(password);
        detached(async move { self.inner.step_up(&password).await }).await
    }

    pub async fn devices(self: Arc<Self>) -> Result<Vec<SyncDevice>, SyncError> {
        let devices = detached(async move { self.inner.devices().await }).await?;
        Ok(devices.into_iter().map(SyncDevice::from).collect())
    }

    pub async fn delete_device(self: Arc<Self>, id: DeviceId) -> Result<(), SyncError> {
        detached(async move { self.inner.delete_device(id).await }).await
    }

    pub async fn logout(self: Arc<Self>) -> Result<(), SyncError> {
        detached(async move { self.inner.logout().await }).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use keygo_api::Method;
    use keygo_api::domain::RevokeReason;
    use keygo_dpop::testing::TestSigner;
    use std::sync::Mutex;
    use std::time::Duration;
    use tokio::sync::Notify;
    use tokio::time::timeout;

    const STORED_TOKEN: &str = "kgr___________________________________________8";
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

    struct TestKey(TestSigner);

    impl DpopKey for TestKey {
        fn public_key(&self) -> Result<Vec<u8>, SignError> {
            Ok(self.0.public_key_sec1())
        }

        fn sign(&self, message: Vec<u8>) -> Result<Vec<u8>, SignError> {
            Ok(self.0.sign_der(&message))
        }
    }

    struct ShortKey;

    impl DpopKey for ShortKey {
        fn public_key(&self) -> Result<Vec<u8>, SignError> {
            Ok(vec![1, 2, 3])
        }

        fn sign(&self, _: Vec<u8>) -> Result<Vec<u8>, SignError> {
            unreachable!()
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
            Arc::new(TestKey(TestSigner::new())),
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

    /// A cancelled Kotlin coroutine drops the future, but the rotated token must still reach the store.
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

    #[test]
    fn bad_public_key_is_rejected_at_construction() {
        let result = SyncClient::new(
            "https://kg.example".into(),
            Arc::new(RecordingTransport::default()),
            Arc::new(ShortKey),
            Arc::new(MemoryStore::default()),
        );

        assert!(matches!(result, Err(SyncError::Client { .. })));
    }

    #[test]
    fn foreign_signer_decodes_what_a_keystore_produces() {
        let expected = TestSigner::new();
        let signer = ForeignSigner::new(Arc::new(TestKey(TestSigner::new()))).unwrap();

        assert_eq!(signer.verifying_key(), expected.verifying_key());
        assert_eq!(
            signer.sign(b"message").unwrap(),
            expected.sign(b"message").unwrap(),
        );
    }
}
