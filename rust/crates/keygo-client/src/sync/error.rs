use keygo_api::domain::RevokeReason;
use keygo_api::{ApiError, ErrorCode};

use super::transport::NetworkError;

pub type SyncResult<T> = Result<T, SyncError>;

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
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
    #[error("signed out")]
    SignedOut { reason: Option<RevokeReason> },
    #[error("rate limited")]
    RateLimited,
    #[error("vault locked")]
    VaultLocked,
    /// The account signed in to holds a different ARK than the unlocked local vault.
    #[error("account mismatch")]
    AccountMismatch,
    #[error("network error: {0}")]
    Network(#[from] NetworkError),
    /// KeyGo answered with one of its own error codes. `code` is the wire name, `unknown` for
    /// a code this client does not know.
    #[error("server error {code}: {message}")]
    Server { code: String, message: String },
    /// Something answered with a body this client cannot use, so likely not a KeyGo server.
    #[error("invalid response: {message}")]
    InvalidResponse { message: String },
    /// A non-KeyGo error body with this status: a reverse proxy, a redirect, a foreign server.
    #[error("unexpected status {status}: {message}")]
    UnexpectedStatus { status: u16, message: String },
    /// A bug or a broken host dependency, never the user's input. `message` is for logs only.
    #[error("internal error: {message}")]
    Internal { message: String },
    /// The server address the user entered is not a plain http(s) origin.
    #[error("invalid server url: {message}")]
    InvalidUrl { message: String },
}

impl SyncError {
    pub(super) fn invalid_response(message: impl Into<String>) -> Self {
        Self::InvalidResponse {
            message: message.into(),
        }
    }
}

/// `session_revoked` and `refresh_expired` never reach this: they end the session first.
pub(super) fn from_response(status: u16, body: &[u8]) -> SyncError {
    let Ok(error) = serde_json::from_slice::<ApiError>(body) else {
        return SyncError::UnexpectedStatus {
            status,
            message: String::from_utf8_lossy(body).chars().take(200).collect(),
        };
    };

    match error.code {
        ErrorCode::InvalidCredentials => SyncError::InvalidCredentials,
        ErrorCode::InvalidUsername => SyncError::InvalidUsername,
        ErrorCode::UsernameTaken => SyncError::UsernameTaken,
        ErrorCode::InviteRequired => SyncError::InviteRequired,
        ErrorCode::InviteInvalid => SyncError::InviteInvalid,
        ErrorCode::FreshAuthRequired => SyncError::FreshAuthRequired,
        ErrorCode::RateLimited => SyncError::RateLimited,
        code => SyncError::Server {
            code: wire_name(code),
            message: error.message,
        },
    }
}

fn wire_name(code: ErrorCode) -> String {
    serde_json::to_value(code)
        .ok()
        .and_then(|v| v.as_str().map(str::to_owned))
        .unwrap_or_else(|| "unknown".into())
}
