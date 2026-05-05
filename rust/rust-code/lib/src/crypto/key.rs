use crate::crypto::error::CryptoResult;
use aead::{Aead, AeadCore, Key, KeyInit};
use zeroize::{Zeroize, ZeroizeOnDrop};

pub trait KeyMaterial: Sized {
    fn try_from_bytes(bytes: &[u8]) -> CryptoResult<Self>;
    fn as_bytes(&self) -> &[u8];
}

pub trait AeadKey: KeyMaterial + Zeroize + ZeroizeOnDrop + Sized {
    type Algorithm: AeadCore + Aead + KeyInit;

    fn key(&self) -> &Key<Self::Algorithm>;
}
