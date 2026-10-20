use crate::error::{OpaqueError, OpaqueResult};
use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use zeroize::Zeroizing;

opaque_newtype! {
    /// Registration, client -> server
    RegistrationRequest(RegistrationRequest) as Vec<u8>;

    /// Registration, server -> client
    RegistrationResponse(RegistrationResponse) as Vec<u8>;

    /// Registration, client -> server
    RegistrationUpload(RegistrationUpload) as Vec<u8>;

    /// Login (KE1), client -> server
    CredentialRequest(CredentialRequest) as Vec<u8>;

    /// Login (KE2), server -> client
    CredentialResponse(CredentialResponse) as Vec<u8>;

    /// Login (KE3), client -> server
    CredentialFinalization(CredentialFinalization) as Vec<u8>;

    /// Per-account server record
    PasswordFile(ServerRegistration) as Zeroizing<Vec<u8>>;
}

macro_rules! wire_message {
    ($($name:ident),+ $(,)?) => {$(
        impl $name {
            pub fn to_base64(&self) -> String {
                URL_SAFE_NO_PAD.encode(self.to_bytes())
            }

            pub fn from_base64(text: &str) -> OpaqueResult<Self> {
                let bytes = URL_SAFE_NO_PAD.decode(text).map_err(|_| OpaqueError::Malformed)?;
                Self::from_bytes(&bytes)
            }
        }

        impl serde::Serialize for $name {
            fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
                serializer.serialize_str(&self.to_base64())
            }
        }

        impl<'de> serde::Deserialize<'de> for $name {
            fn deserialize<D: serde::Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
                let text = String::deserialize(deserializer)?;
                Self::from_base64(&text).map_err(serde::de::Error::custom)
            }
        }
    )+};
}

wire_message!(
    RegistrationRequest,
    RegistrationResponse,
    RegistrationUpload,
    CredentialRequest,
    CredentialResponse,
    CredentialFinalization,
);
