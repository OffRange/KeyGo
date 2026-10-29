use crate::DpopSigner;
use p256::ecdsa::signature::Signer;
use p256::ecdsa::{Signature, SigningKey, VerifyingKey};
use std::convert::Infallible;

pub struct TestSigner {
    key: SigningKey,
    pub(crate) verifying: VerifyingKey,
}

impl TestSigner {
    pub fn new() -> Self {
        Self::from_seed(7)
    }

    pub fn from_seed(seed: u8) -> Self {
        let key = SigningKey::from_slice(&[seed; 32]).unwrap();
        let verifying = *key.verifying_key();
        Self { key, verifying }
    }
}

impl Default for TestSigner {
    fn default() -> Self {
        Self::new()
    }
}

impl DpopSigner for TestSigner {
    type Error = Infallible;

    fn verifying_key(&self) -> &VerifyingKey {
        &self.verifying
    }

    fn sign(&self, msg: &[u8]) -> Result<Signature, Infallible> {
        Ok(self.key.sign(msg))
    }
}
