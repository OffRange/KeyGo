mod htu;
mod jwk;
mod nonce;
mod proof;
mod seen_jtis;
#[cfg(any(test, feature = "test-support"))]
pub mod testing;
mod verify;
mod wire;

use std::time::{SystemTime, UNIX_EPOCH};

pub use jwk::JwkError;
pub use nonce::Nonce;
pub use proof::{DpopError, DpopProver, DpopSigner, ProofRequest};
pub use verify::{Bound, Checked, DpopVerifier, Expected, InvalidProof, VerifyError};
pub use wire::{DecodeError, Htm};

fn unix_now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("system clock is set before the Unix epoch")
        .as_secs()
}
