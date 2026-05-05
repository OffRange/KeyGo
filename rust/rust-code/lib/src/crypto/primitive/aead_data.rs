use crate::crypto::error::{CryptoError, CryptoResult};
use crate::crypto::key::AeadKey;
use aead::{Aead, Generate, KeyInit, Nonce, Payload};
use serde::Serialize;

pub struct AeadCiphertext<K>
where
    K: AeadKey,
{
    ciphertext: Vec<u8>,
    nonce: Nonce<K::Algorithm>,
}

impl<K> AeadCiphertext<K>
where
    K: AeadKey,
{
    pub fn from_parts(ciphertext: Vec<u8>, nonce: Nonce<K::Algorithm>) -> Self {
        Self { ciphertext, nonce }
    }

    pub fn from_parts_bytes(ciphertext: Vec<u8>, nonce: &[u8]) -> Self {
        Self::from_parts(
            ciphertext,
            Nonce::<K::Algorithm>::try_from(nonce).expect("invalid nonce length"),
        )
    }

    pub fn ciphertext(&self) -> &[u8] {
        &self.ciphertext
    }

    pub fn nonce(&self) -> &Nonce<K::Algorithm> {
        &self.nonce
    }

    pub fn nonce_bytes(&self) -> &[u8] {
        self.nonce.as_slice()
    }
}

pub trait AeadEncryptor: AeadKey {
    type Aad: Serialize;

    fn encrypt_data(&self, data: &[u8], aad: &Self::Aad) -> CryptoResult<AeadCiphertext<Self>> {
        let aad_bytes = bcs::to_bytes(aad).map_err(|e| CryptoError::BCS(e.to_string()))?;
        let cipher = Self::Algorithm::new(self.key());
        let nonce = Nonce::<Self::Algorithm>::generate();
        let ciphertext = cipher
            .encrypt(
                &nonce,
                Payload {
                    msg: data,
                    aad: &aad_bytes,
                },
            )
            .map_err(|_| CryptoError::EncryptionFailed)?;

        Ok(AeadCiphertext::from_parts(ciphertext, nonce))
    }

    fn decrypt_data(
        &self,
        ciphertext: &AeadCiphertext<Self>,
        aad: &Self::Aad,
    ) -> CryptoResult<Vec<u8>> {
        let aad_bytes = bcs::to_bytes(aad).map_err(|e| CryptoError::BCS(e.to_string()))?;
        let cipher = Self::Algorithm::new(self.key());
        cipher
            .decrypt(
                ciphertext.nonce(),
                Payload {
                    msg: ciphertext.ciphertext(),
                    aad: &aad_bytes,
                },
            )
            .map_err(|_| CryptoError::DecryptionFailed)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::define_aead_key;
    use aes_gcm_siv::Aes256GcmSiv;
    use serde::Serialize;

    define_aead_key! {
        random struct TestKey(Aes256GcmSiv);
    }

    #[derive(Serialize)]
    struct TestAad {
        scope: &'static str,
    }

    impl AeadEncryptor for TestKey {
        type Aad = TestAad;
    }

    #[test]
    fn round_trip() {
        let key = TestKey::generate_random();
        let aad = TestAad { scope: "rt" };
        let plaintext = b"hello world";

        let ct = key.encrypt_data(plaintext, &aad).unwrap();
        let pt = key.decrypt_data(&ct, &aad).unwrap();

        assert_eq!(pt, plaintext);
    }

    #[test]
    fn wrong_aad_fails() {
        let key = TestKey::generate_random();
        let aad = TestAad { scope: "right" };
        let wrong = TestAad { scope: "wrong" };

        let ct = key.encrypt_data(b"data", &aad).unwrap();
        assert!(matches!(
            key.decrypt_data(&ct, &wrong),
            Err(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn wrong_key_fails() {
        let key = TestKey::generate_random();
        let other = TestKey::generate_random();
        let aad = TestAad { scope: "k" };

        let ct = key.encrypt_data(b"data", &aad).unwrap();
        assert!(matches!(
            other.decrypt_data(&ct, &aad),
            Err(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn tampered_ciphertext_fails() {
        let key = TestKey::generate_random();
        let aad = TestAad { scope: "t" };

        let ct = key.encrypt_data(b"data", &aad).unwrap();
        let mut bytes = ct.ciphertext().to_vec();
        bytes[0] ^= 0x01;
        let tampered = AeadCiphertext::<TestKey>::from_parts_bytes(bytes, ct.nonce_bytes());

        assert!(matches!(
            key.decrypt_data(&tampered, &aad),
            Err(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn nonce_round_trip_via_bytes() {
        let key = TestKey::generate_random();
        let aad = TestAad { scope: "n" };

        let ct = key.encrypt_data(b"payload", &aad).unwrap();
        let rebuilt =
            AeadCiphertext::<TestKey>::from_parts_bytes(ct.ciphertext().to_vec(), ct.nonce_bytes());

        let pt = key.decrypt_data(&rebuilt, &aad).unwrap();
        assert_eq!(pt, b"payload");
    }
}
