//! The key types. They are always nameable, but only hold key material with the `crypto` feature;
//! without it they have no public constructor, so a build without it can never hold a key.

macro_rules! symmetric_key {
    ($($(#[$meta:meta])* $vis:vis struct $name:ident),+ $(,)?) => {
        $(
        $(#[$meta])*
        #[cfg_attr(feature = "crypto", derive(::zeroize::Zeroize, ::zeroize::ZeroizeOnDrop))]
        $vis struct $name {
            #[cfg(feature = "crypto")]
            pub(crate) key: $crate::crypto::CipherKey,
            #[cfg(not(feature = "crypto"))]
            _no_constructor: (),
        }

        #[cfg(feature = "crypto")]
        impl $crate::crypto::KeyMaterial for $name {
            fn from_bytes(bytes: &[u8]) -> $crate::CryptoResult<Self> {
                $crate::crypto::CipherKey::try_from(bytes)
                    .map(|key| Self { key })
                    .map_err(|_| $crate::CryptoError::InvalidKeyLength {
                        expected: $crate::KEY_LEN,
                        got: bytes.len(),
                    })
            }

            fn as_bytes(&self) -> &[u8] {
                self.key.as_slice()
            }
        }

        #[cfg(feature = "crypto")]
        impl $crate::crypto::SymmetricKey for $name {
            fn cipher_key(&self) -> &$crate::crypto::CipherKey {
                &self.key
            }
        }
        )+
    };
}
#[cfg_attr(not(all(test, feature = "crypto")), allow(unused_imports))]
pub(crate) use symmetric_key;

symmetric_key! {
    pub struct OpaqueKek,
    pub struct RootKek,

    pub struct AccountRootKey,
    pub struct VaultKey,
    pub struct ItemKey,

    /// Seals one account-wide record that belongs to no vault.
    pub struct NamespaceKey,

    pub struct BackupKey,
}
