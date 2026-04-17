use crate::crypto::error::{CryptoError, CryptoResult};
use crate::crypto::key::KeyMaterial;
use crate::crypto::primitive::wrap_key::KeyWrapper;
use ed25519_dalek::{SECRET_KEY_LENGTH, Signature, Signer, SigningKey};
use rand::rand_core::UnwrapErr;
use rand::rngs::SysRng;
use std::marker::PhantomData;

pub struct ScopedSigningKey<Wrapper>
where
    Wrapper: KeyWrapper<SigningKey>,
{
    signing_key: SigningKey,
    _wrapper: PhantomData<fn() -> Wrapper>,
}

impl<Wrapper> ScopedSigningKey<Wrapper>
where
    Wrapper: KeyWrapper<SigningKey>,
{
    pub fn generate() -> Self {
        Self {
            signing_key: SigningKey::generate(&mut UnwrapErr(SysRng)),
            _wrapper: PhantomData,
        }
    }

    pub fn unwrap_signing_key(
        wrapped_key: &Wrapper::Wrapped,
        aad: &Wrapper::Aad,
        wrapper: &Wrapper,
    ) -> CryptoResult<Self> {
        let signing_key = wrapper.unwrap_key(wrapped_key, aad)?;
        Ok(Self {
            signing_key,
            _wrapper: PhantomData,
        })
    }

    pub fn wrapped_signing_key(
        &self,
        aad: &Wrapper::Aad,
        wrapper: &Wrapper,
    ) -> CryptoResult<Wrapper::Wrapped> {
        wrapper.wrap_key(&self.signing_key, aad)
    }

    pub fn public_key_bytes(&self) -> [u8; 32] {
        self.signing_key.verifying_key().to_bytes()
    }

    pub fn sign(&self, message: &[u8]) -> Signature {
        self.signing_key.sign(message)
    }

    pub fn verify(&self, message: &[u8], signature: &Signature) -> CryptoResult<()> {
        self.signing_key
            .verify(message, signature)
            .map_err(|_| CryptoError::SignatureInvalid)
    }
}

impl KeyMaterial for SigningKey {
    fn try_from_bytes(bytes: &[u8]) -> CryptoResult<Self> {
        let key = bytes
            .try_into()
            .map_err(|_| CryptoError::InvalidKeyLength {
                expected: SECRET_KEY_LENGTH,
                got: bytes.len(),
            })?;
        Ok(Self::from_bytes(&key))
    }

    fn as_bytes(&self) -> &[u8] {
        self.as_bytes()
    }
}
