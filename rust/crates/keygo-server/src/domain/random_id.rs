use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use thiserror::Error;

const LEN: usize = 16;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Error)]
#[error("{field} must be {LEN} bytes of base64url without padding")]
pub struct InvalidId {
    field: &'static str,
}

/// Declares an opaque handle of 16 random bytes that travels as base64url without padding.
macro_rules! random_id {
    ($($(#[$attr:meta])* $name:ident as $field:literal;)+) => {$(
        $(#[$attr])*
        #[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
        pub struct $name([u8; LEN]);

        impl $name {
            pub fn random() -> Self {
                let mut bytes = [0u8; LEN];
                getrandom::fill(&mut bytes).expect("OS random number generator unavailable");
                Self(bytes)
            }

            pub fn to_base64(self) -> String {
                URL_SAFE_NO_PAD.encode(self.0)
            }

            pub fn from_base64(text: &str) -> Result<Self, InvalidId> {
                const INVALID: InvalidId = InvalidId { field: $field };
                let bytes = URL_SAFE_NO_PAD.decode(text).map_err(|_| INVALID)?;
                bytes.try_into().map(Self).map_err(|_| INVALID)
            }
        }

        impl Serialize for $name {
            fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
                serializer.serialize_str(&self.to_base64())
            }
        }

        impl<'de> Deserialize<'de> for $name {
            fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
                let text = String::deserialize(deserializer)?;
                Self::from_base64(&text).map_err(serde::de::Error::custom)
            }
        }
    )+};
}

random_id! {
    /// Handle for a pending registration, between `register/start` and `register/finish`.
    RegistrationId as "registration_id";

    /// Handle for a login handshake, between `login/start` and `login/finish`.
    LoginId as "login_id";
}

impl RegistrationId {
    pub const fn as_bytes(&self) -> &[u8; LEN] {
        &self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn random_ids_differ() {
        assert_ne!(RegistrationId::random(), RegistrationId::random());
    }

    #[test]
    fn base64_round_trips() {
        let id = RegistrationId::random();

        assert_eq!(RegistrationId::from_base64(&id.to_base64()), Ok(id));
    }

    #[test]
    fn serializes_as_unpadded_base64url() {
        let id = RegistrationId([0xff; LEN]);

        assert_eq!(serde_json::to_value(id).unwrap(), "_____________________w");
    }

    #[test]
    fn rejects_wrong_length_and_alphabet() {
        assert!(RegistrationId::from_base64(&URL_SAFE_NO_PAD.encode([0; 15])).is_err());
        assert!(RegistrationId::from_base64(&URL_SAFE_NO_PAD.encode([0; 17])).is_err());
        assert!(RegistrationId::from_base64("+++++++++++++++++++++w").is_err());
        assert!(RegistrationId::from_base64("_____________________w==").is_err());
    }

    #[test]
    fn error_names_the_field() {
        let err = LoginId::from_base64("").unwrap_err();

        assert!(err.to_string().starts_with("login_id "));
    }
}
