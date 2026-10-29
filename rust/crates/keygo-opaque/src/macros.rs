/// Declares newtypes over `opaque_ke` types instantiated with `KeyGoSuite`, each with a byte codec.
///
/// `as $bytes` picks the `to_bytes` output; use `Zeroizing<Vec<u8>>` for anything secret.
macro_rules! opaque_newtype {
    ($($(#[$attr:meta])* $name:ident($inner:ident) as $bytes:ty;)+) => {$(
        $(#[$attr])*
        pub struct $name(pub(crate) opaque_ke::$inner<$crate::suite::KeyGoSuite>);

        impl $name {
            pub fn to_bytes(&self) -> $bytes {
                self.0.serialize().to_vec().into()
            }

            pub fn from_bytes(bytes: &[u8]) -> $crate::OpaqueResult<Self> {
                Ok(Self(opaque_ke::$inner::deserialize(bytes)?))
            }
        }
    )+};
}

macro_rules! wire_message {
    ($($name:ident),+ $(,)?) => {$(
        impl $name {
            pub fn to_base64(&self) -> String {
                use base64::Engine;
                base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(self.to_bytes())
            }

            pub fn from_base64(text: &str) -> $crate::OpaqueResult<Self> {
                use base64::Engine;
                let bytes = base64::engine::general_purpose::URL_SAFE_NO_PAD
                    .decode(text)
                    .map_err(|_| $crate::OpaqueError::Malformed)?;
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
