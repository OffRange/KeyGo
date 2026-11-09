use keygo_api::domain::AccessToken;
use std::time::{SystemTime, UNIX_EPOCH};

pub(super) struct AccessState {
    pub(super) token: AccessToken,
    /// Unix seconds. The token is usable while the clock reads less than this.
    pub(super) expires_at: u64,
}

/// What the client remembers between calls.
#[derive(Default)]
pub(super) struct AuthState {
    pub(super) access: Option<AccessState>,
    /// The last `DPoP-Nonce` the server sent.
    pub(super) nonce: Option<String>,
    /// Changes whenever `access` does. A call remembers the value its token came with, so when
    /// that token is rejected it can tell whether someone has already replaced it.
    pub(super) generation: u64,
}

impl AuthState {
    /// Forgets the session. `generation` moves on rather than restarting, so a call still holding
    /// a token from before cannot mistake a later one for its own.
    pub(super) fn reset(&mut self) {
        self.access = None;
        self.nonce = None;
        self.generation += 1;
    }
}

pub trait Clock: Send + Sync {
    fn now_secs(&self) -> u64;
}

pub struct SystemClock;

impl Clock for SystemClock {
    fn now_secs(&self) -> u64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0)
    }
}
