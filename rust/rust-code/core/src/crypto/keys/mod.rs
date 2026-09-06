mod account_root_key;
mod item_key;
mod root_kek;
mod signing_key;
mod vault_key;

use crate::crypto::error::CryptoResult;

pub use account_root_key::AccountRootKey;
pub use item_key::{ItemAad, ItemDataAad, ItemKey};
pub use root_kek::RootKEK;
pub use signing_key::ScopedSigningKey;
pub use vault_key::VaultKey;

pub trait TryDeriveFrom<T>: Sized {
    fn try_derive_from(source: T, salt: &[u8], domain: &[u8]) -> CryptoResult<Self>;
}
