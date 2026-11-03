use crate::{KEY_LEN, NONCE_LEN, Sealed};
use aead::array::typenum::Unsigned;
use aead::{Aead, AeadCore, Generate, KeyInit, KeySizeUser, Nonce, Payload};
use aes_gcm_siv::Aes256GcmSiv;
use std::marker::PhantomData;

pub type Cipher = Aes256GcmSiv;
pub type CipherKey = aead::Key<Cipher>;

// `sealed.rs` is compiled without the cipher, so it cannot ask it for these.
const _: () = assert!(KEY_LEN == <<Cipher as KeySizeUser>::KeySize as Unsigned>::USIZE);
const _: () = assert!(NONCE_LEN == <<Cipher as AeadCore>::NonceSize as Unsigned>::USIZE);

impl<T: ?Sized> Sealed<T> {
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
            nonce: nonce.into(),
            ciphertext,
            content: PhantomData,
        })
    }

    pub(crate) fn open(&self, key: &CipherKey, aad: &[u8]) -> Result<Vec<u8>, aead::Error> {
        Cipher::new(key).decrypt(
            &Nonce::<Cipher>::from(self.nonce),
            Payload {
                msg: &self.ciphertext,
                aad,
            },
        )
    }
}
