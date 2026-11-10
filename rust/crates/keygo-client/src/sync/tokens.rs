use async_trait::async_trait;
use keygo_api::domain::RefreshToken;
use keygo_core::DeviceId;

pub struct StoredSession {
    pub device_id: DeviceId,
    pub refresh_token: RefreshToken,
    pub refresh_expires_at: u64,
}

#[derive(Debug, thiserror::Error)]
#[error("{message}")]
pub struct TokenStoreError {
    pub message: String,
}

#[async_trait]
pub trait TokenStore: Send + Sync {
    async fn load(&self) -> Result<Option<StoredSession>, TokenStoreError>;
    async fn save(&self, session: &StoredSession) -> Result<(), TokenStoreError>;
    async fn clear(&self) -> Result<(), TokenStoreError>;
}
