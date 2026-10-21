use moka::Expiry;
use moka::sync::Cache;
use sha2::{Digest, Sha256};
use std::time::{Duration, Instant};

const MAX_ENTRIES: u64 = 100_000;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Record {
    New,
    Replay,
    Full,
}

/// Every `jti` accepted within its proof's acceptance span. Entries are never evicted
/// early: a dropped `jti` could be replayed, so a full cache refuses new proofs instead.
pub(crate) struct SeenJtis(Cache<[u8; 32], Duration>);

struct EntryTtl;

impl Expiry<[u8; 32], Duration> for EntryTtl {
    fn expire_after_create(&self, _: &[u8; 32], ttl: &Duration, _: Instant) -> Option<Duration> {
        Some(*ttl)
    }
}

impl SeenJtis {
    pub(crate) fn new() -> Self {
        Self(Cache::builder().expire_after(EntryTtl).build())
    }

    pub(crate) fn record(&self, jkt: &[u8; 32], jti: &str, ttl: Duration) -> Record {
        let key: [u8; 32] = Sha256::new()
            .chain_update(jkt)
            .chain_update(jti)
            .finalize()
            .into();

        if self.0.contains_key(&key) {
            return Record::Replay;
        }
        if self.0.entry_count() >= MAX_ENTRIES {
            return Record::Full;
        }

        if self.0.entry(key).or_insert(ttl).is_fresh() {
            Record::New
        } else {
            Record::Replay
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const TTL: Duration = Duration::from_mins(2);

    #[test]
    fn a_jti_is_accepted_once() {
        let seen = SeenJtis::new();

        assert_eq!(seen.record(&[1; 32], "jti", TTL), Record::New);
        assert_eq!(seen.record(&[1; 32], "jti", TTL), Record::Replay);
    }

    #[test]
    fn jtis_are_scoped_to_the_key() {
        let seen = SeenJtis::new();

        assert_eq!(seen.record(&[1; 32], "jti", TTL), Record::New);
        assert_eq!(seen.record(&[2; 32], "jti", TTL), Record::New);
    }

    #[test]
    fn concurrent_copies_are_accepted_once() {
        let seen = SeenJtis::new();

        let accepted = std::thread::scope(|scope| {
            let handles: Vec<_> = (0..8)
                .map(|_| scope.spawn(|| seen.record(&[1; 32], "jti", TTL) == Record::New))
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
    fn a_jti_can_be_reused_after_its_ttl() {
        let seen = SeenJtis::new();
        seen.record(&[1; 32], "jti", Duration::from_millis(10));
        std::thread::sleep(Duration::from_millis(50));

        assert_eq!(
            seen.record(&[1; 32], "jti", Duration::from_millis(10)),
            Record::New
        );
    }
}
