use super::InviteHash;
use std::fmt::{self, Display, Formatter};
use std::str::FromStr;
use thiserror::Error;

const MIN_LEN: usize = 5;
const MAX_LEN: usize = 64;

/// The invite's hash in hex, cut where no other open invite shares the prefix.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct InviteId(String);

impl InviteId {
    pub fn new(hash: &InviteHash, open: &[InviteHash]) -> Self {
        let shared = open
            .iter()
            .filter(|other| *other != hash)
            .map(|other| hash.shared_hex_digits(other))
            .max()
            .unwrap_or(0);

        let mut id = hash.to_string();
        id.truncate((shared + 1).clamp(MIN_LEN, id.len()));
        Self(id)
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Debug, Error)]
pub enum ParseInviteIdError {
    #[error("must be at least {MIN_LEN} hex characters, got {0}")]
    TooShort(usize),
    #[error("must be at most {MAX_LEN} hex characters, got {0}")]
    TooLong(usize),
    #[error("{0:?} is not a hex digit")]
    InvalidCharacter(char),
}

impl FromStr for InviteId {
    type Err = ParseInviteIdError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let id = s.trim().to_ascii_lowercase();
        if let Some(c) = id.chars().find(|c| !c.is_ascii_hexdigit()) {
            return Err(ParseInviteIdError::InvalidCharacter(c));
        }
        if id.len() < MIN_LEN {
            return Err(ParseInviteIdError::TooShort(id.len()));
        }
        if id.len() > MAX_LEN {
            return Err(ParseInviteIdError::TooLong(id.len()));
        }

        Ok(Self(id))
    }
}

impl Display for InviteId {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::invite_hash as hash;

    fn id(hash: InviteHash, open: &[InviteHash]) -> String {
        InviteId::new(&hash, open).to_string()
    }

    #[test]
    fn lone_invite_gets_the_minimum_length() {
        let a = hash(&[0x5b, 0xe2, 0x0f]);

        assert_eq!(id(a, &[a]), "5be20");
    }

    #[test]
    fn distinct_prefixes_stay_at_the_minimum_length() {
        let a = hash(&[0x5b, 0xe2, 0x0f]);
        let b = hash(&[0xe8, 0x1d, 0x4f]);

        assert_eq!(id(a, &[a, b]), "5be20");
    }

    #[test]
    fn shared_prefix_grows_to_the_first_difference() {
        let a = hash(&[0xa3, 0xf9, 0xc1, 0x70]);
        let b = hash(&[0xa3, 0xf9, 0xc1, 0xe0]);

        assert_eq!(id(a, &[a, b]), "a3f9c17");
        assert_eq!(id(b, &[a, b]), "a3f9c1e");
    }

    #[test]
    fn longest_shared_prefix_decides() {
        let a = hash(&[0xa3, 0xf9, 0xc1, 0x70]);
        let near = hash(&[0xa3, 0xf9, 0xc1, 0x7f]);
        let far = hash(&[0xa3, 0xf9, 0x00]);

        assert_eq!(id(a, &[far, a, near]), "a3f9c170");
    }

    #[test]
    fn hashes_differing_only_in_the_last_char_need_all_64() {
        let a = [0xaa; 32];
        let mut b = a;
        b[31] = 0xab;

        assert_eq!(id(a.into(), &[a.into(), b.into()]).len(), 64);
    }

    #[test]
    fn parses_odd_length_ids_case_insensitively() {
        let id: InviteId = " A3F9C17 ".parse().unwrap();

        assert_eq!(id.to_string(), "a3f9c17");
    }

    #[test]
    fn parse_rejects_ids_list_never_prints() {
        assert!(matches!(
            "a3f9".parse::<InviteId>(),
            Err(ParseInviteIdError::TooShort(4))
        ));
        assert!(matches!(
            "a".repeat(65).parse::<InviteId>(),
            Err(ParseInviteIdError::TooLong(65))
        ));
        assert!(matches!(
            "a3f9%".parse::<InviteId>(),
            Err(ParseInviteIdError::InvalidCharacter('%'))
        ));
    }
}
