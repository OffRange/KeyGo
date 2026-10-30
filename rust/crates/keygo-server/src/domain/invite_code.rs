use super::InviteHash;
use hmac::{Hmac, KeyInit, Mac};
use sha2::Sha256;
use std::fmt::{Display, Formatter};
use std::str::FromStr;
use thiserror::Error;
use zeroize::{Zeroize, ZeroizeOnDrop};

const ALPHABET: &[u8; 32] = b"0123456789ABCDEFGHJKMNPQRSTVWXYZ"; // no I, L, O, U

#[derive(Zeroize, ZeroizeOnDrop)]
pub struct InvitePepper([u8; 32]);

impl InvitePepper {
    pub fn new(bytes: &[u8; 32]) -> Self {
        Self(*bytes)
    }

    pub fn hash(&self, code: &InviteCode) -> InviteHash {
        let mut mac = Hmac::<Sha256>::new_from_slice(&self.0).expect("HMAC accepts any key length");
        mac.update(&code.0);
        <[u8; 32]>::from(mac.finalize().into_bytes()).into()
    }
}

#[derive(Clone, Copy, PartialEq, Eq, Hash)]
pub struct InviteCode([u8; 12]);

impl InviteCode {
    pub fn generate() -> Self {
        let mut code = [0; 12];
        getrandom::fill(&mut code).expect("getrandom failed");
        for b in &mut code {
            *b = ALPHABET[(*b & 0x1F) as usize];
        }
        InviteCode(code)
    }

    pub fn as_str(&self) -> &str {
        std::str::from_utf8(&self.0).expect("invite code is ASCII")
    }
}

#[derive(Debug, Error)]
pub enum ParseInviteCodeError {
    #[error("too short: {0} of 12 characters")]
    TooShort(usize),
    #[error("too long: more than 12 characters")]
    TooLong,
    #[error("invalid char: {0:?}")]
    InvalidCharacter(char),
}

impl FromStr for InviteCode {
    type Err = ParseInviteCodeError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let mut code = [0; 12];
        let mut len = 0;

        for c in s.chars() {
            let c = match c.to_ascii_uppercase() {
                c if c == '-' || c.is_whitespace() => continue,
                'O' => '0',
                'I' | 'L' => '1',
                c @ ('0'..='9' | 'A'..='Z') if c != 'U' => c,
                _ => return Err(ParseInviteCodeError::InvalidCharacter(c)),
            };

            if len == code.len() {
                return Err(ParseInviteCodeError::TooLong);
            }
            code[len] = c as u8;
            len += 1;
        }

        if len != code.len() {
            return Err(ParseInviteCodeError::TooShort(len));
        }

        Ok(InviteCode(code))
    }
}

impl Display for InviteCode {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        let s = self.as_str();
        write!(f, "{}-{}-{}", &s[0..4], &s[4..8], &s[8..12])
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn pepper(byte: u8) -> InvitePepper {
        InvitePepper::new(&[byte; 32])
    }

    #[test]
    fn spellings_of_one_code_hash_alike() {
        let a: InviteCode = "7KQ2M9XD4HPA".parse().unwrap();
        let b: InviteCode = "7kq2-m9xd 4hpa".parse().unwrap();

        assert_eq!(pepper(1).hash(&a), pepper(1).hash(&b));
    }

    #[test]
    fn hash_depends_on_the_pepper() {
        let code = InviteCode::generate();

        assert_ne!(pepper(1).hash(&code), pepper(2).hash(&code));
    }

    #[test]
    fn hash_is_hmac_sha256_of_the_canonical_code() {
        let code: InviteCode = "7KQ2-M9XD-4HPA".parse().unwrap();
        let mut mac = Hmac::<Sha256>::new_from_slice(&[1; 32]).unwrap();
        mac.update(b"7KQ2M9XD4HPA");

        assert_eq!(
            pepper(1).hash(&code).as_bytes(),
            &<[u8; 32]>::from(mac.finalize().into_bytes())
        );
    }
}
