use keygo_core::crypto::{ItemId, ItemKey, NamespaceId, SymmetricKey, UserId, VaultId, VaultKey};
use uuid::Uuid;

uniffi::custom_type!(Uuid, String, {
    remote,
    try_lift: |s| Uuid::parse_str(&s).map_err(|e| uniffi::deps::anyhow::anyhow!("{e}")),
    lower: |u| u.to_string(),
});

uniffi::custom_type!(UserId, Uuid, {
    remote,
    try_lift: |u| Ok(UserId(u)),
    lower: |id| id.0,
});

uniffi::custom_type!(VaultId, Uuid, {
    remote,
    try_lift: |u| Ok(VaultId(u)),
    lower: |id| id.0,
});

uniffi::custom_type!(ItemId, Uuid, {
    remote,
    try_lift: |u| Ok(ItemId(u)),
    lower: |id| id.0,
});

uniffi::custom_type!(NamespaceId, Uuid, {
    remote,
    try_lift: |u| Ok(NamespaceId(u)),
    lower: |id| id.0,
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
