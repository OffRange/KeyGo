#![forbid(unsafe_code)]

//! The key hierarchy, the ids it binds to and the sealed blobs it produces.
//!
//! Without the `crypto` feature this is only names and storage: `Sealed<VaultKey>` can be parsed,
//! stored and passed on, but no key can ever be created or read. That is the server's view.

#[cfg(feature = "crypto")]
mod crypto;
mod error;
mod ids;
mod keys;
mod sealed;

#[cfg(feature = "crypto")]
pub use crypto::*;
pub use error::{CryptoError, CryptoResult};
pub use ids::{DeviceId, ItemId, NamespaceId, SessionId, UserId, VaultId};
pub use keys::*;
pub use sealed::{KEY_LEN, NONCE_LEN, Sealed, TAG_LEN};
