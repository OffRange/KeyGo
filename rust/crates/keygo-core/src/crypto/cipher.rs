use crate::crypto::{CryptoError, CryptoResult};
use aead::array::typenum::Unsigned;
use aead::{Aead, AeadCore, Generate, KeyInit, KeySizeUser, Nonce, Payload};
use aes_gcm_siv::Aes256GcmSiv;
use serde::Serialize;
use std::marker::PhantomData;

pub type Cipher = Aes256GcmSiv;
pub type CipherKey = aead::Key<Cipher>;

pub const KEY_LEN: usize = <<Cipher as KeySizeUser>::KeySize as Unsigned>::USIZE;
pub const NONCE_LEN: usize = <<Cipher as AeadCore>::NonceSize as Unsigned>::USIZE;

pub struct Sealed<T: ?Sized> {
    nonce: Nonce<Cipher>,
    ciphertext: Vec<u8>,
    content: PhantomData<fn() -> Box<T>>,
}

impl<T: ?Sized> Sealed<T> {
    pub fn from_parts(nonce: &[u8], ciphertext: Vec<u8>) -> CryptoResult<Self> {
        let nonce =
            Nonce::<Cipher>::try_from(nonce).map_err(|_| CryptoError::InvalidNonceLength {
                expected: NONCE_LEN,
                got: nonce.len(),
            })?;
        Ok(Self {
            nonce,
            ciphertext,
            content: PhantomData,
        })
    }

    pub fn nonce(&self) -> &[u8] {
        self.nonce.as_slice()
    }

    pub fn ciphertext(&self) -> &[u8] {
        &self.ciphertext
    }

    /// `(nonce, ciphertext)`
    pub fn into_parts(self) -> (Vec<u8>, Vec<u8>) {
        (self.nonce.to_vec(), self.ciphertext)
    }

    pub(crate) fn seal(key: &CipherKey, plaintext: &[u8], aad: &[u8]) -> Result<Self, aead::Error> {
        let nonce = Nonce::<Cipher>::generate();
        let ciphertext = Cipher::new(key).encrypt(
            &nonce,
            Payload {
                msg: plaintext,
                aad,
            },
        )?;
        Ok(Self {
            nonce,
            ciphertext,
            content: PhantomData,
        })
    }

    pub(crate) fn open(&self, key: &CipherKey, aad: &[u8]) -> Result<Vec<u8>, aead::Error> {
        Cipher::new(key).decrypt(
            &self.nonce,
            Payload {
                msg: &self.ciphertext,
                aad,
            },
        )
    }
}

/// AADs are BCS-encoded; the encoding is part of the stored format.
pub(crate) fn encode_aad(aad: &(impl Serialize + ?Sized)) -> CryptoResult<Vec<u8>> {
    Ok(bcs::to_bytes(aad)?)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn from_parts_rejects_a_wrong_length_nonce() {
        assert!(matches!(
            Sealed::<[u8]>::from_parts(&[0; 8], vec![]),
            Err(CryptoError::InvalidNonceLength {
                expected: NONCE_LEN,
                got: 8,
            })
        ));
    }

    #[test]
    fn parts_round_trip() {
        let sealed = Sealed::<[u8]>::from_parts(&[7; NONCE_LEN], vec![1, 2, 3]).unwrap();
        assert_eq!(sealed.into_parts(), (vec![7; NONCE_LEN], vec![1, 2, 3]));
    }
}
