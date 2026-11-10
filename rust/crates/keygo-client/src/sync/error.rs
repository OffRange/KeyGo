use keygo_api::domain::RevokeReason;
use keygo_api::{ApiError, ErrorCode};

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
    #[error("network error: {message}")]
    Network { message: String },
    #[error("server error {code}: {message}")]
    Server { code: String, message: String },
    #[error("client error: {message}")]
    Client { message: String },
    #[error(transparent)]
    InvalidUrl(#[from] url::ParseError),
}

impl SyncError {
    pub(super) fn invalid_response(message: impl Into<String>) -> Self {
        Self::Server {
            code: "invalid_response".into(),
            message: message.into(),
        }
    }
}

/// `session_revoked` and `refresh_expired` never reach this: they end the session first.
pub(super) fn from_response(status: u16, body: &[u8]) -> SyncError {
    let Ok(error) = serde_json::from_slice::<ApiError>(body) else {
        return SyncError::Server {
            code: format!("http_{status}"),
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
