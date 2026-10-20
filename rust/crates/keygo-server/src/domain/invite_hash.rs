use std::array::TryFromSliceError;
use std::fmt::{self, Display, Formatter};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct InviteHash([u8; 32]);

impl InviteHash {
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }

    pub fn shared_hex_digits(&self, other: &Self) -> usize {
        self.hex_digits()
            .zip(other.hex_digits())
            .take_while(|(a, b)| a == b)
            .count()
    }

    fn hex_digits(&self) -> impl Iterator<Item = u8> {
        self.0.iter().flat_map(|byte| [byte >> 4, byte & 0x0F])
    }
}

impl From<[u8; 32]> for InviteHash {
    fn from(bytes: [u8; 32]) -> Self {
        Self(bytes)
    }
}

impl TryFrom<&[u8]> for InviteHash {
    type Error = TryFromSliceError;

    fn try_from(bytes: &[u8]) -> Result<Self, Self::Error> {
        bytes.try_into().map(Self)
    }
}

impl Display for InviteHash {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        f.write_str(&hex::encode(self.0))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn displays_as_lowercase_hex() {
        let hash = InviteHash::from([0xab; 32]);

        assert_eq!(hash.to_string(), "ab".repeat(32));
    }

    #[test]
    fn shared_digits_count_half_bytes() {
        let a = InviteHash::from([0xaa; 32]);
        let mut b = [0xaa; 32];
        b[1] = 0xab;

        assert_eq!(a.shared_hex_digits(&b.into()), 3);
    }

    #[test]
    fn equal_hashes_share_every_digit() {
        let a = InviteHash::from([0xaa; 32]);

        assert_eq!(a.shared_hex_digits(&a), 64);
    }

    #[test]
    fn only_32_bytes_convert() {
        assert!(InviteHash::try_from([0; 31].as_slice()).is_err());
        assert!(InviteHash::try_from([0; 32].as_slice()).is_ok());
    }
}
