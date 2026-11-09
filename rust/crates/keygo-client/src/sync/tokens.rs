use async_trait::async_trait;
use keygo_api::domain::RefreshToken;
use keygo_core::DeviceId;

pub struct StoredSession {
    pub device_id: DeviceId,
    pub refresh_token: RefreshToken,
    pub refresh_expires_at: u64,
}

impl StoredSession {
    /// A second copy of the session. `RefreshToken` is not `Clone`, so it goes through its encoding.
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

/// Durable home of the refresh token. The host decides how it is protected.
#[async_trait]
pub trait TokenStore: Send + Sync {
    async fn load(&self) -> Result<Option<StoredSession>, TokenStoreError>;
    async fn save(&self, session: StoredSession) -> Result<(), TokenStoreError>;
    async fn clear(&self) -> Result<(), TokenStoreError>;
}
