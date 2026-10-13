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
