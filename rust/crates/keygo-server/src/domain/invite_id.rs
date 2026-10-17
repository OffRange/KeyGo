use super::InviteHash;
use std::fmt::{self, Display, Formatter};

const MIN_LEN: usize = 5;

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
}

impl Display for InviteId {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn hash(prefix: &[u8]) -> InviteHash {
        let mut hash = [0; 32];
        hash[..prefix.len()].copy_from_slice(prefix);
        hash.into()
    }

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
}
