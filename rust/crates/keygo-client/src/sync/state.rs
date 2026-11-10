use super::tokens::StoredSession;
use keygo_api::auth::Access;
use keygo_api::domain::AccessToken;
use std::time::{SystemTime, UNIX_EPOCH};

pub(super) struct AccessState {
    pub(super) token: AccessToken,
    /// Unix seconds. The token is usable while the clock reads less than this.
    pub(super) expires_at: u64,
}

#[derive(Default)]
pub(super) struct AuthState {
    access: Option<AccessState>,
    /// Moves whenever `access` changes. A call whose token was rejected uses it to tell whether
    /// someone has already replaced that token.
    pub(super) generation: u64,
    /// A rotated session the store has not accepted yet. Until it does, the store may still hold
    /// the spent token, which the server would treat as reuse, so this copy wins over the store.
    pub(super) unsaved: Option<StoredSession>,
}

impl AuthState {
    pub(super) fn access(&self) -> Option<&AccessState> {
        self.access.as_ref()
    }

    /// The only way to set the access token, so `generation` cannot be left behind.
    ///
    /// `sent_at` is read before the request, so a token never looks like it lives longer than it does.
    pub(super) fn replace_access(&mut self, access: Access, sent_at: u64) {
        self.access = Some(AccessState {
            token: access.access_token,
            expires_at: sent_at.saturating_add(access.access_expires_in),
        });
        self.generation += 1;
    }

    /// `generation` moves on rather than restarting, so a call holding an old token cannot mistake
    /// a new one for its own.
    pub(super) fn reset(&mut self) {
        self.access = None;
        self.unsaved = None;
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
        state.replace_access(
            Access {
                access_token: serde_json::from_value(serde_json::json!("a.b.c")).unwrap(),
                access_expires_in: 1,
            },
            0,
        );
        assert_eq!(state.generation, before + 1);
        assert!(state.access().is_some());
    }
}
