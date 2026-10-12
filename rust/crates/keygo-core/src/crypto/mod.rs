pub(crate) mod aad;
mod cipher;
mod error;
mod ids;
pub(crate) mod kdf;
mod key;
mod keys;

pub use aad::Aad;
pub(crate) use aad::aad;
pub use cipher::{Cipher, CipherKey, KEY_LEN, NONCE_LEN, Sealed};
pub use error::{CryptoError, CryptoResult};
pub use ids::{ItemId, NamespaceId, UserId, VaultId};
pub(crate) use key::symmetric_key;
pub use key::{Encrypts, SymmetricKey, Wraps};
pub use keys::{
    AccountRootKey, ItemAad, ItemDataAad, ItemKey, NamespaceDataAad, NamespaceKey, NamespaceKeyAad,
    RootKEK, SecretKind, VaultKey,
};

#[cfg(test)]
mod golden_tests;
