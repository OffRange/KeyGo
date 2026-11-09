use std::time::{SystemTime, UNIX_EPOCH};

/// What the client remembers between calls. Later tasks add the nonce and the access token.
#[derive(Default)]
pub(super) struct AuthState {}

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
