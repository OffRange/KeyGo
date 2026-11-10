use crate::DpopSigner;
use p256::ecdsa::signature::Signer;
use p256::ecdsa::{Signature, SigningKey, VerifyingKey};
use std::convert::Infallible;

pub struct TestSigner(SigningKey);

impl TestSigner {
    pub fn new() -> Self {
        Self::from_seed(7)
    }

    pub fn from_seed(seed: u8) -> Self {
        Self(SigningKey::from_slice(&[seed; 32]).unwrap())
    }

    /// Matches what a platform keystore exports.
    pub fn public_key_sec1(&self) -> Vec<u8> {
        self.0
            .verifying_key()
            .to_sec1_point(false)
            .as_bytes()
            .to_vec()
    }

    /// Matches what a platform keystore produces.
    pub fn sign_der(&self, msg: &[u8]) -> Vec<u8> {
        let sig: Signature = self.0.sign(msg);
        sig.to_der().as_bytes().to_vec()
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
        self.0.verifying_key()
    }

    fn sign(&self, msg: &[u8]) -> Result<Signature, Infallible> {
        Ok(self.0.sign(msg))
    }
}
