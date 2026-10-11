use crate::crypto::cipher::encode_aad;
use crate::crypto::{CipherKey, CryptoError, CryptoResult, Sealed};
use serde::Serialize;
use zeroize::{ZeroizeOnDrop, Zeroizing};

pub trait SymmetricKey: Sized + ZeroizeOnDrop {
    fn from_bytes(bytes: &[u8]) -> CryptoResult<Self>;

    fn as_bytes(&self) -> &[u8] {
        self.cipher_key().as_slice()
    }

    #[doc(hidden)]
    fn cipher_key(&self) -> &CipherKey;
}

pub trait Wraps<K: SymmetricKey>: SymmetricKey {
    type Aad: Serialize + ?Sized;

    fn wrap_key(&self, key: &K, aad: &Self::Aad) -> CryptoResult<Sealed<K>> {
        Sealed::seal(self.cipher_key(), key.as_bytes(), &encode_aad(aad)?)
            .map_err(|_| CryptoError::KeyWrapFailed)
    }

    fn unwrap_key(&self, wrapped: &Sealed<K>, aad: &Self::Aad) -> CryptoResult<K> {
        let bytes = wrapped
            .open(self.cipher_key(), &encode_aad(aad)?)
            .map(Zeroizing::new)
            .map_err(|_| CryptoError::KeyUnwrapFailed)?;
        K::from_bytes(&bytes)
    }
}

pub trait Encrypts: SymmetricKey {
    type Aad: Serialize + ?Sized;

    fn encrypt(&self, plaintext: &[u8], aad: &Self::Aad) -> CryptoResult<Sealed<[u8]>> {
        Sealed::seal(self.cipher_key(), plaintext, &encode_aad(aad)?)
            .map_err(|_| CryptoError::EncryptionFailed)
    }

    fn decrypt(&self, sealed: &Sealed<[u8]>, aad: &Self::Aad) -> CryptoResult<Vec<u8>> {
        sealed
            .open(self.cipher_key(), &encode_aad(aad)?)
            .map_err(|_| CryptoError::DecryptionFailed)
    }
}

macro_rules! symmetric_key {
    ($(#[$meta:meta])* $vis:vis struct $name:ident;) => {
        $(#[$meta])*
        #[derive(::zeroize::Zeroize, ::zeroize::ZeroizeOnDrop)]
        $vis struct $name($crate::crypto::CipherKey);

        impl $crate::crypto::SymmetricKey for $name {
            fn from_bytes(bytes: &[u8]) -> $crate::crypto::CryptoResult<Self> {
                $crate::crypto::CipherKey::try_from(bytes).map(Self).map_err(|_| {
                    $crate::crypto::CryptoError::InvalidKeyLength {
                        expected: $crate::crypto::KEY_LEN,
                        got: bytes.len(),
                    }
                })
            }

            fn cipher_key(&self) -> &$crate::crypto::CipherKey {
                &self.0
            }
        }
    };
}
pub(crate) use symmetric_key;

#[cfg(test)]
mod tests {
    use super::*;

    symmetric_key! {
        struct TestWrappingKey;
    }
    symmetric_key! {
        struct TestKey;
    }

    #[derive(Serialize)]
    struct TestAad {
        scope: &'static str,
    }

    impl Wraps<TestKey> for TestWrappingKey {
        type Aad = TestAad;
    }

    impl Encrypts for TestKey {
        type Aad = TestAad;
    }

    fn wrapping_key(byte: u8) -> TestWrappingKey {
        TestWrappingKey::from_bytes(&[byte; 32]).unwrap()
    }

    fn key(byte: u8) -> TestKey {
        TestKey::from_bytes(&[byte; 32]).unwrap()
    }

    fn aad(scope: &'static str) -> TestAad {
        TestAad { scope }
    }

    #[test]
    fn from_bytes_rejects_a_wrong_length() {
        assert!(matches!(
            TestKey::from_bytes(&[0; 16]),
            Err(CryptoError::InvalidKeyLength {
                expected: 32,
                got: 16,
            })
        ));
    }

    #[test]
    fn wrap_round_trip() {
        let wrapped = wrapping_key(7).wrap_key(&key(1), &aad("rt")).unwrap();
        let unwrapped = wrapping_key(7).unwrap_key(&wrapped, &aad("rt")).unwrap();

        assert_eq!(unwrapped.as_bytes(), key(1).as_bytes());
    }

    #[test]
    fn unwrap_with_the_wrong_key_fails() {
        let wrapped = wrapping_key(7).wrap_key(&key(1), &aad("k")).unwrap();

        assert!(matches!(
            wrapping_key(8).unwrap_key(&wrapped, &aad("k")),
            Err(CryptoError::KeyUnwrapFailed)
        ));
    }

    #[test]
    fn unwrap_with_the_wrong_aad_fails() {
        let wrapped = wrapping_key(7).wrap_key(&key(1), &aad("right")).unwrap();

        assert!(matches!(
            wrapping_key(7).unwrap_key(&wrapped, &aad("wrong")),
            Err(CryptoError::KeyUnwrapFailed)
        ));
    }

    #[test]
    fn encrypt_round_trip() {
        let sealed = key(1).encrypt(b"hello world", &aad("rt")).unwrap();

        assert_eq!(key(1).decrypt(&sealed, &aad("rt")).unwrap(), b"hello world");
    }

    #[test]
    fn decrypt_with_the_wrong_key_fails() {
        let sealed = key(1).encrypt(b"data", &aad("k")).unwrap();

        assert!(matches!(
            key(2).decrypt(&sealed, &aad("k")),
            Err(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn decrypt_with_the_wrong_aad_fails() {
        let sealed = key(1).encrypt(b"data", &aad("right")).unwrap();

        assert!(matches!(
            key(1).decrypt(&sealed, &aad("wrong")),
            Err(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn decrypt_of_a_tampered_ciphertext_fails() {
        let sealed = key(1).encrypt(b"data", &aad("t")).unwrap();
        let (nonce, mut ciphertext) = sealed.into_parts();
        ciphertext[0] ^= 0x01;
        let tampered = Sealed::from_parts(&nonce, ciphertext).unwrap();

        assert!(matches!(
            key(1).decrypt(&tampered, &aad("t")),
            Err(CryptoError::DecryptionFailed)
        ));
    }
}
