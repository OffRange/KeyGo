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
    access: Option<AccessState>,
    /// The last `DPoP-Nonce` the server sent.
    pub(super) nonce: Option<String>,
    /// Changes whenever `access` does. A call remembers the value its token came with, so when
    /// that token is rejected it can tell whether someone has already replaced it.
    pub(super) generation: u64,
}

impl AuthState {
    pub(super) fn access(&self) -> Option<&AccessState> {
        self.access.as_ref()
    }

    /// The only way to set the access token, so `generation` cannot be left behind.
    pub(super) fn replace_access(&mut self, access: AccessState) {
        self.access = Some(access);
        self.generation += 1;
    }

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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn replacing_access_moves_the_generation() {
        let mut state = AuthState::default();
        let before = state.generation;
        state.replace_access(AccessState {
            token: serde_json::from_value(serde_json::json!("a.b.c")).unwrap(),
            expires_at: 1,
        });
        assert_eq!(state.generation, before + 1);
        assert!(state.access().is_some());
    }
}
