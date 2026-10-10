use keygo_core::crypto::{ItemKey, SymmetricKey, VaultKey};
use uuid::Uuid;

uniffi::custom_type!(Uuid, String, {
    remote,
    try_lift: |s| Uuid::parse_str(&s).map_err(|e| uniffi::deps::anyhow::anyhow!("{e}")),
    lower: |u| u.to_string(),
});

uniffi::custom_type!(VaultKey, Vec<u8>, {
    remote,
    try_lift: |bytes| VaultKey::from_bytes(&bytes).map_err(|e| uniffi::deps::anyhow::anyhow!("{e}")),
    lower: |key| key.as_bytes().to_vec(),
});

uniffi::custom_type!(ItemKey, Vec<u8>, {
    remote,
    try_lift: |bytes| ItemKey::from_bytes(&bytes).map_err(|e| uniffi::deps::anyhow::anyhow!("{e}")),
    lower: |key| key.as_bytes().to_vec(),
});
