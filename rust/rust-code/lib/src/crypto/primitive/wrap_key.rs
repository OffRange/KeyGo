use crate::crypto::error::{CryptoError, CryptoResult};
use crate::crypto::key::{AeadKey, KeyMaterial};
use aead::{Aead, Generate, KeyInit, Nonce, Payload};
use serde::Serialize;
use std::marker::PhantomData;
use zeroize::Zeroizing;

pub trait WrappedKey<Target, Wrapper>: Sized
where
    Target: KeyMaterial,
    Wrapper: AeadKey,
{
    fn from_parts(ciphertext: Vec<u8>, nonce: Nonce<Wrapper::Algorithm>) -> Self;
    fn ciphertext(&self) -> &[u8];
    fn nonce(&self) -> &Nonce<Wrapper::Algorithm>;
    fn nonce_bytes(&self) -> &[u8] {
        self.nonce().as_slice()
    }
}

pub struct AeadWrappedKey<Target, Wrapper>
where
    Target: KeyMaterial,
    Wrapper: AeadKey,
{
    ciphertext: Vec<u8>,
    nonce: Nonce<Wrapper::Algorithm>,
    _target: PhantomData<fn() -> Target>,
}

impl<Target, Wrapper> WrappedKey<Target, Wrapper> for AeadWrappedKey<Target, Wrapper>
where
    Target: KeyMaterial,
    Wrapper: AeadKey,
{
    fn from_parts(ciphertext: Vec<u8>, nonce: Nonce<Wrapper::Algorithm>) -> Self {
        Self {
            ciphertext,
            nonce,
            _target: PhantomData,
        }
    }

    fn ciphertext(&self) -> &[u8] {
        &self.ciphertext
    }

    fn nonce(&self) -> &Nonce<Wrapper::Algorithm> {
        &self.nonce
    }
}

pub trait KeyWrapper<Target>: AeadKey
where
    Target: KeyMaterial,
{
    type Aad: Serialize;
    type Wrapped: WrappedKey<Target, Self>;

    fn wrap_key(&self, key_to_wrap: &Target, aad: &Self::Aad) -> CryptoResult<Self::Wrapped> {
        let aad = bcs::to_bytes(aad).map_err(|e| CryptoError::BCS(e.to_string()))?;
        wrap_bytes::<Target, Self>(self.key(), key_to_wrap.as_bytes(), &aad)
    }

    fn unwrap_key(&self, wrapped_key: &Self::Wrapped, aad: &Self::Aad) -> CryptoResult<Target> {
        let aad = bcs::to_bytes(aad).map_err(|e| CryptoError::BCS(e.to_string()))?;
        let unwrapped = unwrap_bytes::<Target, Self>(self.key(), wrapped_key, &aad)?;
        Target::try_from_bytes(&unwrapped)
    }
}

fn wrap_bytes<Target, Wrapper>(
    key: &aead::Key<Wrapper::Algorithm>,
    key_to_wrap: &[u8],
    aad: &[u8],
) -> CryptoResult<Wrapper::Wrapped>
where
    Target: KeyMaterial,
    Wrapper: KeyWrapper<Target>,
{
    let cipher = Wrapper::Algorithm::new(key);
    let nonce = Nonce::<Wrapper::Algorithm>::generate();
    let ciphertext = cipher
        .encrypt(
            &nonce,
            Payload {
                msg: key_to_wrap,
                aad,
            },
        )
        .map_err(|_| CryptoError::KeyWrapFailed)?;

    Ok(Wrapper::Wrapped::from_parts(ciphertext, nonce))
}

fn unwrap_bytes<Target, Wrapper>(
    key: &aead::Key<Wrapper::Algorithm>,
    wrapped_key: &Wrapper::Wrapped,
    aad: &[u8],
) -> CryptoResult<Zeroizing<Vec<u8>>>
where
    Target: KeyMaterial,
    Wrapper: KeyWrapper<Target>,
{
    let cipher = Wrapper::Algorithm::new(key);
    let plaintext = cipher
        .decrypt(
            wrapped_key.nonce(),
            Payload {
                msg: wrapped_key.ciphertext(),
                aad,
            },
        )
        .map_err(|_| CryptoError::KeyUnwrapFailed)?;

    Ok(Zeroizing::new(plaintext))
}

#[cfg(test)]
mod tests {
    use super::KeyWrapper;
    use crate::crypto::error::{CryptoError, CryptoResult};
    use crate::crypto::key::{AeadKey, KeyMaterial};
    use aead::Key;
    use aes_gcm_siv::Aes256GcmSiv;
    use serde::Serialize;
    use zeroize::{Zeroize, ZeroizeOnDrop};

    #[derive(Serialize)]
    struct TestAad {
        context: &'static str,
    }

    #[derive(Zeroize, ZeroizeOnDrop)]
    struct TestWrappingKey(Key<Aes256GcmSiv>);

    struct TestWrappedSecret {
        bytes: [u8; 32],
    }

    type TestWrappedBlob = super::AeadWrappedKey<TestWrappedSecret, TestWrappingKey>;

    impl KeyMaterial for TestWrappingKey {
        fn try_from_bytes(bytes: &[u8]) -> CryptoResult<Self> {
            let key = Key::<Aes256GcmSiv>::try_from(bytes).map_err(|_| {
                CryptoError::InvalidKeyLength {
                    expected: 32,
                    got: bytes.len(),
                }
            })?;

            Ok(Self(key))
        }

        fn as_bytes(&self) -> &[u8] {
            self.0.as_slice()
        }
    }

    impl AeadKey for TestWrappingKey {
        type Algorithm = Aes256GcmSiv;

        fn key(&self) -> &Key<Self::Algorithm> {
            &self.0
        }
    }

    impl KeyMaterial for TestWrappedSecret {
        fn try_from_bytes(bytes: &[u8]) -> CryptoResult<Self> {
            let bytes: [u8; 32] = bytes
                .try_into()
                .map_err(|_| CryptoError::InvalidKeyLength {
                    expected: 32,
                    got: bytes.len(),
                })?;
            if bytes[0] != 0xAA {
                return Err(CryptoError::InvalidKey);
            }

            Ok(Self { bytes })
        }

        fn as_bytes(&self) -> &[u8] {
            &self.bytes
        }
    }

    impl KeyWrapper<TestWrappedSecret> for TestWrappingKey {
        type Aad = TestAad;
        type Wrapped = TestWrappedBlob;
    }

    fn test_wrapping_key(byte: u8) -> TestWrappingKey {
        TestWrappingKey::try_from_bytes(&[byte; 32]).unwrap()
    }

    fn test_wrapped_secret() -> TestWrappedSecret {
        let mut bytes = [0x11; 32];
        bytes[0] = 0xAA;
        TestWrappedSecret { bytes }
    }

    fn invalid_wrapped_secret() -> TestWrappedSecret {
        TestWrappedSecret { bytes: [0x11; 32] }
    }

    #[test]
    fn round_trip() {
        let wrapping_key = test_wrapping_key(7);
        let wrapped_secret = test_wrapped_secret();
        let aad = TestAad {
            context: "round-trip",
        };

        let wrapped = wrapping_key.wrap_key(&wrapped_secret, &aad).unwrap();
        let unwrapped = wrapping_key.unwrap_key(&wrapped, &aad).unwrap();

        assert_eq!(wrapped_secret.as_bytes(), unwrapped.as_bytes());
    }

    #[test]
    fn wrong_wrapping_key_fails() {
        let wrapping_key = test_wrapping_key(7);
        let wrong_key = test_wrapping_key(8);
        let wrapped_secret = test_wrapped_secret();
        let aad = TestAad {
            context: "wrong-key",
        };

        let wrapped = wrapping_key.wrap_key(&wrapped_secret, &aad).unwrap();

        assert!(matches!(
            wrong_key.unwrap_key(&wrapped, &aad),
            Err(CryptoError::KeyUnwrapFailed)
        ));
    }

    #[test]
    fn wrong_aad_fails() {
        let wrapping_key = test_wrapping_key(7);
        let wrapped_secret = test_wrapped_secret();
        let correct_aad = TestAad { context: "correct" };
        let wrong_aad = TestAad { context: "wrong" };

        let wrapped = wrapping_key
            .wrap_key(&wrapped_secret, &correct_aad)
            .unwrap();

        assert!(matches!(
            wrapping_key.unwrap_key(&wrapped, &wrong_aad),
            Err(CryptoError::KeyUnwrapFailed)
        ));
    }

    #[test]
    fn invalid_key_material_fails_after_decrypt() {
        let wrapping_key = test_wrapping_key(7);
        let invalid_secret = invalid_wrapped_secret();
        let aad = TestAad {
            context: "invalid-material",
        };

        let wrapped = wrapping_key.wrap_key(&invalid_secret, &aad).unwrap();

        assert!(matches!(
            wrapping_key.unwrap_key(&wrapped, &aad),
            Err(CryptoError::InvalidKey)
        ));
    }
}
