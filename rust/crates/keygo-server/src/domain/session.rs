use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use serde::{Serialize, Serializer};
use sha2::{Digest, Sha256};
use uuid::Uuid;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

pub type SessionId = Uuid;

const PREFIX: &str = "kgr_";
// 32 --> 43 if encoded using b64urlsafe
const ENCODED_LEN: usize = PREFIX.len() + 43;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum RevokeReason {
    Replaced,
}

impl RevokeReason {
    pub const fn as_str(&self) -> &'static str {
        match self {
            RevokeReason::Replaced => "replaced",
        }
    }
}

#[derive(Zeroize, ZeroizeOnDrop)]
pub struct RefreshToken([u8; 32]);

impl RefreshToken {
    pub fn generate() -> Self {
        let mut token = [0u8; 32];
        getrandom::fill(token.as_mut_slice()).expect("OS random number generator unavailable");
        Self(token)
    }

    pub fn expose_encoded(&self) -> Zeroizing<String> {
        let mut out = Zeroizing::new(String::with_capacity(ENCODED_LEN));
        out.push_str(PREFIX);
        URL_SAFE_NO_PAD.encode_string(self.0, &mut out);
        out
    }

    pub fn digest(&self) -> [u8; 32] {
        Sha256::digest(self.0).into()
    }
}

impl Serialize for RefreshToken {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(&self.expose_encoded())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generated_tokens_differ() {
        assert_ne!(
            RefreshToken::generate().digest(),
            RefreshToken::generate().digest(),
        );
    }

    #[test]
    fn encodes_as_prefixed_unpadded_base64url() {
        let encoded = RefreshToken([0xff; 32]).expose_encoded();

        assert_eq!(
            encoded.as_str(),
            "kgr___________________________________________8"
        );
        assert_eq!(encoded.len(), ENCODED_LEN);
    }

    #[test]
    fn encoding_round_trips_the_secret() {
        let token = RefreshToken::generate();
        let encoded = token.expose_encoded();

        let raw = URL_SAFE_NO_PAD
            .decode(encoded.strip_prefix(PREFIX).unwrap())
            .unwrap();

        assert_eq!(raw, token.0);
    }

    #[test]
    fn serializes_as_the_encoded_token() {
        let token = RefreshToken::generate();

        assert_eq!(
            serde_json::to_value(&token).unwrap(),
            token.expose_encoded().as_str(),
        );
    }

    #[test]
    fn digest_is_sha256_of_the_raw_secret() {
        assert_eq!(
            hex::encode(RefreshToken([0; 32]).digest()),
            "66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925",
        );
    }
}
