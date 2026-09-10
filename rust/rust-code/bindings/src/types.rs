//! Uniffi custom-type registrations shared across the bindings crate.
//!
//! Nothing imports this module. The registrations take effect by being compiled, through the
//! `uniffi::custom_type!` macro, not by being referenced from other code, so `cargo` sees no
//! caller and a reference-based cleanup pass would flag it as dead. `item.rs`, `vault.rs` and
//! `ark_session.rs` all rely on `Uuid` and `VaultKey` crossing the FFI boundary, so deleting this
//! module would silently break every signature that uses either type.

use keygo_core::crypto::{KeyMaterial, VaultKey};
use uuid::Uuid;

uniffi::custom_type!(Uuid, String, {
    remote,
    try_lift: |s| Uuid::parse_str(&s).map_err(|e| uniffi::deps::anyhow::anyhow!("{e}")),
    lower: |u| u.to_string(),
});

uniffi::custom_type!(VaultKey, Vec<u8>, {
    remote,
    try_lift: |bytes| {
        VaultKey::try_from_bytes(&bytes)
            .map_err(|e| uniffi::deps::anyhow::anyhow!("{e:?}"))
    },
    lower: |key| key.as_bytes().to_vec(),
});
