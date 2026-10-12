use crate::crypto::{CryptoResult, UserId, VaultId};
use serde::Serialize;

/// AADs are BCS-encoded; the encoding is part of the stored format.
pub trait Aad: Serialize {}

macro_rules! aad {
    ($(#[$meta:meta])* $vis:vis struct $name:ident { $($body:tt)* }) => {
        $(#[$meta])*
        #[derive(::serde::Serialize)]
        $vis struct $name { $($body)* }

        impl $crate::crypto::Aad for $name {}
    };
}
pub(crate) use aad;

impl Aad for UserId {}
impl Aad for VaultId {}

pub(crate) fn encode_aad(aad: &impl Aad) -> CryptoResult<Vec<u8>> {
    Ok(bcs::to_bytes(aad)?)
}
