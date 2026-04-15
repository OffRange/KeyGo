pub mod account_root_key;
pub mod signing_key;
pub mod root_kek;
pub mod vault_key;

use crate::crypto::error::CryptoResult;
pub use account_root_key::*;
pub use root_kek::*;
pub use signing_key::*;
pub use vault_key::*;

pub trait TryDeriveFrom<T>: Sized {
    fn try_derive_from(source: T, salt: &[u8], domain: &[u8]) -> CryptoResult<Self>;
}