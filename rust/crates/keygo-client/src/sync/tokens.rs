use async_trait::async_trait;
use keygo_api::domain::RefreshToken;
use keygo_core::DeviceId;

pub struct StoredSession {
    pub device_id: DeviceId,
    pub refresh_token: RefreshToken,
    pub refresh_expires_at: u64,
}

impl StoredSession {
    /// `RefreshToken` is not `Clone`, so this copies through its encoding.
    pub(super) fn duplicate(&self) -> Self {
        Self {
            device_id: self.device_id,
            refresh_token: self
                .refresh_token
                .expose_encoded()
                .parse()
                .expect("a refresh token parses its own encoding"),
            refresh_expires_at: self.refresh_expires_at,
        }
    }
}

#[derive(Debug, thiserror::Error)]
#[error("{message}")]
pub struct TokenStoreError {
    pub message: String,
}

/// `save` runs under the client's auth lock, so it must return in bounded time: a hung `save`
/// blocks every later call, including logout. A refused `save` is retried on later calls.
#[async_trait]
pub trait TokenStore: Send + Sync {
    async fn load(&self) -> Result<Option<StoredSession>, TokenStoreError>;
    async fn save(&self, session: &StoredSession) -> Result<(), TokenStoreError>;
    async fn clear(&self) -> Result<(), TokenStoreError>;
}
