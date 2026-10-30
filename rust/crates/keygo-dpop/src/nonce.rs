use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use hmac::{Hmac, KeyInit, Mac};
use sha2::Sha256;
use std::fmt::{Display, Formatter};
use std::time::Duration;
use zeroize::{Zeroize, ZeroizeOnDrop};

const NONCE_LIFETIME: Duration = Duration::from_mins(5);

const TIMESTAMP_LEN: usize = size_of::<u64>();
/// HMAC-SHA256 truncated to its leftmost 16 bytes.
const TAG_LEN: usize = 16;
const NONCE_LEN: usize = TIMESTAMP_LEN + TAG_LEN;

#[derive(Zeroize, ZeroizeOnDrop)]
pub struct NonceKey([u8; 32]);

impl NonceKey {
    pub fn generate() -> Self {
        let mut key = Self([0u8; 32]);
        getrandom::fill(key.0.as_mut_slice()).expect("OS random number generator unavailable");
        key
    }

    #[cfg(test)]
    pub(crate) fn new(bytes: [u8; 32]) -> Self {
        Self(bytes)
    }

    fn mac(&self, created_at: u64) -> Hmac<Sha256> {
        let mut mac = Hmac::<Sha256>::new_from_slice(&self.0).expect("HMAC accepts any key length");
        mac.update(&created_at.to_be_bytes());
        mac
    }

    fn tag(&self, created_at: u64) -> [u8; TAG_LEN] {
        self.mac(created_at).finalize().into_bytes()[..TAG_LEN]
            .try_into()
            .expect("SHA-256 output is longer than TAG_LEN")
    }

    pub(crate) fn issue(&self, now: u64) -> Nonce {
        Nonce {
            created_at: now,
            tag: self.tag(now),
        }
    }

    /// Returns the nonce if this key issued it. Freshness is the caller's call: it depends on the
    /// server start, which the key does not know.
    pub(crate) fn open(&self, value: &str) -> Option<Nonce> {
        let bytes: [u8; NONCE_LEN] = URL_SAFE_NO_PAD.decode(value).ok()?.try_into().ok()?;
        let (created_at, tag) = bytes
            .split_first_chunk::<TIMESTAMP_LEN>()
            .expect("NONCE_LEN holds the timestamp");
        let created_at = u64::from_be_bytes(*created_at);

        // `verify_truncated_left` checks only as many bytes as it is given; `bytes` has a fixed
        // length, so the tag is always exactly TAG_LEN.
        self.mac(created_at).verify_truncated_left(tag).ok()?;

        Some(Nonce {
            created_at,
            tag: tag.try_into().expect("NONCE_LEN holds the tag after it"),
        })
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Nonce {
    created_at: u64,
    tag: [u8; TAG_LEN],
}

impl Nonce {
    pub(crate) fn expires_at(&self) -> u64 {
        self.created_at + NONCE_LIFETIME.as_secs()
    }
}

impl Display for Nonce {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        let mut bytes = [0u8; NONCE_LEN];
        bytes[..TIMESTAMP_LEN].copy_from_slice(&self.created_at.to_be_bytes());
        bytes[TIMESTAMP_LEN..].copy_from_slice(&self.tag);

        f.write_str(&URL_SAFE_NO_PAD.encode(bytes))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const NOW: u64 = 1_700_000_000;

    fn key() -> NonceKey {
        NonceKey::new([1; 32])
    }

    #[test]
    fn opens_own_nonce() {
        let nonce = key().issue(NOW);

        assert_eq!(key().open(&nonce.to_string()), Some(nonce));
    }

    #[test]
    fn encodes_created_at_big_endian_before_tag() {
        let bytes = URL_SAFE_NO_PAD
            .decode(key().issue(NOW).to_string())
            .unwrap();

        assert_eq!(bytes.len(), NONCE_LEN);
        assert_eq!(&bytes[..TIMESTAMP_LEN], &NOW.to_be_bytes());
    }

    #[test]
    fn expires_after_lifetime() {
        assert_eq!(
            key().issue(NOW).expires_at(),
            NOW + NONCE_LIFETIME.as_secs()
        );
    }

    #[test]
    fn rejects_nonce_from_another_key() {
        let foreign = NonceKey::new([2; 32]).issue(NOW).to_string();

        assert_eq!(key().open(&foreign), None);
    }

    #[test]
    fn rejects_shifted_timestamp() {
        let mut bytes = URL_SAFE_NO_PAD
            .decode(key().issue(NOW).to_string())
            .unwrap();
        bytes[TIMESTAMP_LEN - 1] ^= 1;

        assert_eq!(key().open(&URL_SAFE_NO_PAD.encode(bytes)), None);
    }

    #[test]
    fn rejects_malformed_nonce() {
        let truncated = &key().issue(NOW).to_string()[..10];

        assert_eq!(key().open("not base64!"), None);
        assert_eq!(key().open(truncated), None);
    }
}
