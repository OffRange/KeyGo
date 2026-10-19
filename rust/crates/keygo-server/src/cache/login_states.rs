use crate::domain::LoginId;
use keygo_opaque::server::ServerLogin;
use moka::ops::compute::{CompResult, Op};
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
        // Not `Cache::remove`: that returns an entry even after it expired, until maintenance drops
        // it. `and_compute_with` reads the entry the way `get` does, so it never sees an expired one,
        // and it runs calls on the same key one at a time, so a state can be taken only once.
        let result = self
            .0
            .entry_by_ref(key)
            .and_compute_with(|entry| match entry {
                Some(_) => Op::Remove,
                None => Op::Nop,
            });
        match result {
            CompResult::Removed(entry) => Some(entry.into_value()),
            _ => None,
        }
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
    fn concurrent_takes_get_the_state_once() {
        let states = LoginStates::new();
        let id = LoginId::random();
        states.insert(id, pending());

        let taken = std::thread::scope(|scope| {
            let handles: Vec<_> = (0..8)
                .map(|_| scope.spawn(|| states.take(&id).is_some()))
                .collect();
            handles
                .into_iter()
                .map(|handle| handle.join().unwrap())
                .filter(|&taken| taken)
                .count()
        });

        assert_eq!(taken, 1);
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
