mod htu;
pub mod jwk;
mod nonce;
mod proof;
mod seen_jtis;
#[cfg(test)]
mod testing;
pub mod verify;
pub mod wire;

use std::time::{SystemTime, UNIX_EPOCH};

pub use nonce::{Nonce, NonceKey};
pub use proof::{DpopError, DpopProver, DpopSigner, ProofRequest};
pub use wire::Htm;

fn unix_now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("system clock is set before the Unix epoch")
        .as_secs()
}
