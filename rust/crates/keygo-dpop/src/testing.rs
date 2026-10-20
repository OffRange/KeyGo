use crate::DpopSigner;
use p256::ecdsa::signature::Signer;
use p256::ecdsa::{Signature, SigningKey, VerifyingKey};
use std::convert::Infallible;

pub(crate) struct TestSigner {
    key: SigningKey,
    pub(crate) verifying: VerifyingKey,
}

impl TestSigner {
    pub(crate) fn new() -> Self {
        Self::from_seed(7)
    }

    pub(crate) fn from_seed(seed: u8) -> Self {
        let key = SigningKey::from_slice(&[seed; 32]).unwrap();
        let verifying = *key.verifying_key();
        Self { key, verifying }
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
