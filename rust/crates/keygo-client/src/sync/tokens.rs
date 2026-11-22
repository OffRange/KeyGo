use super::client::Account;
use async_trait::async_trait;
use keygo_api::domain::{RefreshToken, Username};
use keygo_core::{DeviceId, UserId};
use url::Url;

pub struct StoredSession {
    pub server: Url,
    pub user_id: UserId,
    pub username: Username,
    pub device_id: DeviceId,
    pub refresh_token: RefreshToken,
    pub refresh_expires_at: u64,
}

pub(super) struct SessionOwner {
    pub(super) user_id: UserId,
    pub(super) username: Username,
}

impl From<&StoredSession> for SessionOwner {
    fn from(session: &StoredSession) -> Self {
        Self {
            user_id: session.user_id,
            username: session.username.clone(),
        }
    }
}

impl From<&Account> for SessionOwner {
    fn from(account: &Account) -> Self {
        Self {
            user_id: account.user_id,
            username: account.username.clone(),
        }
    }
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
