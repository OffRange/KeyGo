use crate::domain::LoginId;
use keygo_opaque::server::ServerLogin;
use moka::sync::Cache;
use std::time::Duration;
use uuid::Uuid;

const TTL: Duration = Duration::from_mins(1);
const MAX_ENTRIES: u64 = 10_000;

#[derive(Clone)]
pub struct Pending {
    /// `None` on the fake path for an unknown username.
    pub user_id: Option<Uuid>,
    pub state: ServerLogin,
}

#[derive(Clone)]
pub struct LoginStates(Cache<LoginId, Pending>);

impl LoginStates {
    pub fn new() -> Self {
        Self::with_ttl(TTL)
    }

    pub fn with_ttl(ttl: Duration) -> Self {
        Self(
            Cache::builder()
                .max_capacity(MAX_ENTRIES)
                .time_to_live(ttl)
                .build(),
        )
    }

    pub fn insert(&self, key: LoginId, value: Pending) {
        self.0.insert(key, value);
    }

    pub fn take(&self, key: &LoginId) -> Option<Pending> {
        self.0.remove(key)
    }

    #[cfg(test)]
    pub fn count(&self) -> u64 {
        self.0.run_pending_tasks();
        self.0.entry_count()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use keygo_opaque::client::ClientLogin;
    use keygo_opaque::server::ServerSetup;

    fn pending() -> Pending {
        let (_, request) = ClientLogin::start(b"password").unwrap();
        let (state, _) =
            ServerLogin::start(&ServerSetup::generate(), None, request, b"identifier").unwrap();
        Pending {
            user_id: None,
            state,
        }
    }

    #[test]
    fn a_state_is_taken_once() {
        let states = LoginStates::new();
        let id = LoginId::random();
        states.insert(id, pending());

        assert!(states.take(&id).is_some());
        assert!(states.take(&id).is_none());
    }

    #[test]
    fn an_expired_state_cannot_be_taken() {
        let states = LoginStates::with_ttl(Duration::from_millis(10));
        let id = LoginId::random();
        states.insert(id, pending());
        std::thread::sleep(Duration::from_millis(50));

        assert!(states.take(&id).is_none());
    }
}
