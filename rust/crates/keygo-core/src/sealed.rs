use crate::error::{CryptoError, CryptoResult};
use std::marker::PhantomData;

pub const KEY_LEN: usize = 32;
pub const NONCE_LEN: usize = 12;

pub struct Sealed<T: ?Sized> {
    pub(crate) nonce: [u8; NONCE_LEN],
    pub(crate) ciphertext: Vec<u8>,
    pub(crate) content: PhantomData<fn() -> Box<T>>,
}

impl<T: ?Sized> Sealed<T> {
    pub fn from_parts(nonce: &[u8], ciphertext: Vec<u8>) -> CryptoResult<Self> {
        let nonce = nonce
            .try_into()
            .map_err(|_| CryptoError::InvalidNonceLength {
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
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::VaultKey;

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

    #[test]
    fn a_wrapped_key_is_nameable_in_every_build() {
        let wrapped = Sealed::<VaultKey>::from_parts(&[7; NONCE_LEN], vec![1, 2, 3]).unwrap();
        assert_eq!(wrapped.nonce(), [7; NONCE_LEN]);
        assert_eq!(wrapped.ciphertext(), [1, 2, 3]);
    }
}
