mod cipher;
mod error;
mod ids;
pub(crate) mod kdf;
mod key;
mod keys;

pub use cipher::{Cipher, CipherKey, KEY_LEN, NONCE_LEN, Sealed};
pub use error::{CryptoError, CryptoResult};
pub use ids::{ItemId, UserId, VaultId};
pub(crate) use key::symmetric_key;
pub use key::{Encrypts, SymmetricKey, Wraps};
pub use keys::{AccountRootKey, ItemAad, ItemKey, RootKEK, VaultKey};

#[cfg(test)]
mod golden_tests;
