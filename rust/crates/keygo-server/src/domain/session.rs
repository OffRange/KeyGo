use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use sha2::{Digest, Sha256};
use std::str::FromStr;
use std::time::Duration;
use thiserror::Error;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

pub const SESSION_LIFETIME: Duration = Duration::from_hours(180 * 24);
pub const REFRESH_TTL: Duration = Duration::from_hours(30 * 24);
pub const SESSION_RETENTION: Duration = Duration::from_hours(30 * 24);

const PREFIX: &str = "kgr_";
const TOKEN_BYTES: usize = 32;
const ENCODED_LEN: usize = PREFIX.len()
    + base64::encoded_len(TOKEN_BYTES, false).expect("encoded length can be represented by usize");

macro_rules! revoke_reasons {
    ($($name:ident => $str:literal),+ $(,)?) => {
        #[derive(Debug, Clone, Copy, PartialEq, Eq)]
        pub enum RevokeReason {
            $($name,)+
        }

        impl RevokeReason {
            #[cfg(test)]
            pub const ALL: &[Self] = &[$(Self::$name,)+];

            pub const fn as_str(self) -> &'static str {
                match self {
                    $(Self::$name => $str,)+
                }
            }
        }

        #[derive(Debug, Error)]
        #[error("unknown revoke reason {0:?}")]
        pub struct UnknownRevokeReason(String);

        impl FromStr for RevokeReason {
            type Err = UnknownRevokeReason;

            fn from_str(s: &str) -> Result<Self, Self::Err> {
                match s {
                    $($str => Ok(Self::$name),)+
                    _ => Err(UnknownRevokeReason(s.to_owned())),
                }
            }
        }
    };
}

revoke_reasons! {
    Replaced => "replaced",
    ReuseDetected => "reuse_detected",
    Logout => "logout",
}

#[derive(Zeroize, ZeroizeOnDrop)]
pub struct RefreshToken([u8; TOKEN_BYTES]);

impl RefreshToken {
    pub fn generate() -> Self {
        let mut token = [0u8; TOKEN_BYTES];
        getrandom::fill(token.as_mut_slice()).expect("OS random number generator unavailable");
        Self(token)
    }

    pub fn expose_encoded(&self) -> Zeroizing<String> {
        let mut out = Zeroizing::new(String::with_capacity(ENCODED_LEN));
        out.push_str(PREFIX);
        URL_SAFE_NO_PAD.encode_string(self.0, &mut out);
        out
    }

    pub fn digest(&self) -> [u8; TOKEN_BYTES] {
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

#[derive(Debug, Error)]
pub enum ParseRefreshTokenError {
    #[error("invalid prefix")]
    InvalidPrefix,
    #[error("invalid token")]
    InvalidToken,
}

impl FromStr for RefreshToken {
    type Err = ParseRefreshTokenError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let Some(token) = s.strip_prefix(PREFIX) else {
            return Err(ParseRefreshTokenError::InvalidPrefix);
        };

        if token.len() != ENCODED_LEN - PREFIX.len() {
            return Err(ParseRefreshTokenError::InvalidToken);
        }

        let mut out = RefreshToken([0; TOKEN_BYTES]);
        URL_SAFE_NO_PAD
            .decode_slice(token, &mut out.0)
            .map_err(|_| ParseRefreshTokenError::InvalidToken)?;

        Ok(out)
    }
}

impl<'de> Deserialize<'de> for RefreshToken {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        let text = Zeroizing::new(String::deserialize(deserializer)?);
        text.parse().map_err(serde::de::Error::custom)
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
        let encoded = RefreshToken([0xff; TOKEN_BYTES]).expose_encoded();

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
    fn revoke_reason_round_trips_its_stored_name() {
        for &reason in RevokeReason::ALL {
            assert_eq!(reason.as_str().parse::<RevokeReason>().unwrap(), reason);
        }
        assert!("logout_typo".parse::<RevokeReason>().is_err());
    }

    #[test]
    fn parse_round_trips_the_encoding() {
        let token = RefreshToken::generate();

        let parsed: RefreshToken = token.expose_encoded().parse().unwrap();

        assert_eq!(parsed.0, token.0);
    }

    #[test]
    fn parse_rejects_a_missing_prefix() {
        let encoded = RefreshToken::generate().expose_encoded();
        let unprefixed = encoded.strip_prefix(PREFIX).unwrap();

        assert!(matches!(
            unprefixed.parse::<RefreshToken>(),
            Err(ParseRefreshTokenError::InvalidPrefix),
        ));
        assert!(matches!(
            format!("kga_{unprefixed}").parse::<RefreshToken>(),
            Err(ParseRefreshTokenError::InvalidPrefix),
        ));
    }

    #[test]
    fn parse_rejects_the_wrong_length() {
        let encoded = RefreshToken::generate().expose_encoded();

        for text in [
            &encoded[..ENCODED_LEN - 1],
            &format!("{}A", encoded.as_str()),
        ] {
            assert!(matches!(
                text.parse::<RefreshToken>(),
                Err(ParseRefreshTokenError::InvalidToken),
            ));
        }
    }

    #[test]
    fn parse_rejects_non_base64url() {
        let text = format!("{PREFIX}{}", "+".repeat(ENCODED_LEN - PREFIX.len()));

        assert!(matches!(
            text.parse::<RefreshToken>(),
            Err(ParseRefreshTokenError::InvalidToken),
        ));
    }

    #[test]
    fn parse_rejects_non_canonical_trailing_bits() {
        // Same bytes as the canonical "…8", but the unused low bits of the last char are set.
        let text = "kgr___________________________________________9";

        assert!(matches!(
            text.parse::<RefreshToken>(),
            Err(ParseRefreshTokenError::InvalidToken),
        ));
    }

    #[test]
    fn deserializes_from_the_encoded_token() {
        let token = RefreshToken::generate();
        let json = serde_json::to_value(&token).unwrap();

        let parsed: RefreshToken = serde_json::from_value(json).unwrap();

        assert_eq!(parsed.0, token.0);
    }

    #[test]
    fn deserialize_rejects_a_malformed_token() {
        assert!(serde_json::from_str::<RefreshToken>(r#""kgr_short""#).is_err());
        assert!(
            serde_json::from_value::<RefreshToken>(serde_json::json!(vec![0u8; TOKEN_BYTES]))
                .is_err()
        );
    }

    #[test]
    fn digest_is_sha256_of_the_raw_secret() {
        assert_eq!(
            hex::encode(RefreshToken([0; TOKEN_BYTES]).digest()),
            "66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925",
        );
    }
}
