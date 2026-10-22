use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::sync::{Mutex, PoisonError};

const CAPACITY: usize = 100_000;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Record {
    New,
    Replay,
    Full,
}

/// The `jti` of every accepted proof, kept until that proof could no longer be accepted.
///
/// Entries are never dropped early: a dropped `jti` could be replayed, so a full store refuses new
/// proofs instead. Deadlines are seconds on the clock the verifier judges proofs by, so a clock
/// step cannot expire an entry while its proof is still acceptable.
pub(crate) struct SeenJtis {
    capacity: usize,
    state: Mutex<State>,
}

struct State {
    /// SHA-256(`jkt` ‖ `jti`) → the last second at which its proof can be accepted.
    deadlines: HashMap<[u8; 32], u64>,
    /// No entry expires before this second. A lower bound: it is exact again after every sweep.
    earliest_deadline: u64,
}

impl SeenJtis {
    pub(crate) fn new() -> Self {
        Self::with_capacity(CAPACITY)
    }

    pub(crate) fn with_capacity(capacity: usize) -> Self {
        Self {
            capacity,
            state: Mutex::new(State {
                deadlines: HashMap::with_capacity(capacity),
                earliest_deadline: u64::MAX,
            }),
        }
    }

    pub(crate) fn record(&self, jkt: &[u8; 32], jti: &str, deadline: u64, now: u64) -> Record {
        debug_assert!(deadline >= now, "the proof is acceptable now");

        let key: [u8; 32] = Sha256::new()
            .chain_update(jkt)
            .chain_update(jti)
            .finalize()
            .into();

        let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);

        // An expired entry counts as absent: correctness never waits for a sweep.
        if state.deadlines.get(&key).is_some_and(|&d| d >= now) {
            return Record::Replay;
        }
        if state.deadlines.len() >= self.capacity {
            state.remove_expired(now);
            if state.deadlines.len() >= self.capacity {
                return Record::Full;
            }
        }

        state.deadlines.insert(key, deadline);
        state.earliest_deadline = state.earliest_deadline.min(deadline);
        Record::New
    }
}

impl State {
    fn remove_expired(&mut self, now: u64) {
        if self.earliest_deadline >= now {
            return;
        }

        let mut earliest = u64::MAX;
        self.deadlines.retain(|_, &mut deadline| {
            let live = deadline >= now;
            if live {
                earliest = earliest.min(deadline);
            }
            live
        });
        self.earliest_deadline = earliest;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const NOW: u64 = 1_700_000_000;
    const KEY: [u8; 32] = [1; 32];

    #[test]
    fn a_jti_is_accepted_once() {
        let seen = SeenJtis::new();

        assert_eq!(seen.record(&KEY, "jti", NOW, NOW), Record::New);
        assert_eq!(seen.record(&KEY, "jti", NOW, NOW), Record::Replay);
    }

    #[test]
    fn jtis_are_scoped_to_the_key() {
        let seen = SeenJtis::new();

        assert_eq!(seen.record(&[1; 32], "jti", NOW, NOW), Record::New);
        assert_eq!(seen.record(&[2; 32], "jti", NOW, NOW), Record::New);
    }

    #[test]
    fn concurrent_copies_are_accepted_once() {
        let seen = SeenJtis::new();

        let accepted = std::thread::scope(|scope| {
            let handles: Vec<_> = (0..8)
                .map(|_| scope.spawn(|| seen.record(&KEY, "jti", NOW, NOW) == Record::New))
                .collect();
            handles
                .into_iter()
                .map(|handle| handle.join().unwrap())
                .filter(|&accepted| accepted)
                .count()
        });

        assert_eq!(accepted, 1);
    }

    #[test]
    fn a_jti_is_kept_until_its_deadline() {
        let seen = SeenJtis::new();
        seen.record(&KEY, "jti", NOW + 60, NOW);

        assert_eq!(seen.record(&KEY, "jti", NOW + 60, NOW + 60), Record::Replay);
        assert_eq!(seen.record(&KEY, "jti", NOW + 61, NOW + 61), Record::New);
    }

    #[test]
    fn a_full_store_refuses_new_jtis() {
        let seen = SeenJtis::with_capacity(2);
        seen.record(&KEY, "a", NOW, NOW);
        seen.record(&KEY, "b", NOW, NOW);

        assert_eq!(seen.record(&KEY, "c", NOW, NOW), Record::Full);
        assert_eq!(seen.record(&KEY, "a", NOW, NOW), Record::Replay);
    }

    #[test]
    fn a_full_store_makes_room_once_entries_expire() {
        let seen = SeenJtis::with_capacity(1);
        seen.record(&KEY, "a", NOW, NOW);

        assert_eq!(seen.record(&KEY, "b", NOW, NOW), Record::Full);
        assert_eq!(seen.record(&KEY, "b", NOW + 1, NOW + 1), Record::New);
    }

    #[test]
    fn making_room_keeps_live_entries() {
        let seen = SeenJtis::with_capacity(2);
        seen.record(&KEY, "long", NOW + 100, NOW);
        seen.record(&KEY, "short", NOW + 1, NOW);

        assert_eq!(seen.record(&KEY, "new", NOW + 2, NOW + 2), Record::New);
        assert_eq!(
            seen.record(&KEY, "long", NOW + 100, NOW + 2),
            Record::Replay
        );
    }
}
