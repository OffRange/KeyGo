use lib::crypto::types::{UserId, VaultId};
use lib::crypto::{AccountRootKey, KeyMaterial, VaultKey};
use lib::item::account::Account;
use lib::item::create_account::CreateAccount;
use lib::item::vault::Vault;
use std::sync::Arc;
use uuid::Uuid;

uniffi::custom_type!(Uuid, String, {
    remote,
    try_lift: |s| Uuid::parse_str(&s).map_err(|e| uniffi::deps::anyhow::anyhow!("{e}")),
    lower: |u| u.to_string(),
});

uniffi::custom_type!(AccountRootKey, Vec<u8>, {
    remote,
    try_lift: |bytes| {
        AccountRootKey::try_from_bytes(&bytes)
            .map_err(|e| uniffi::deps::anyhow::anyhow!("{e:?}"))
    },
    lower: |key| key.as_bytes().to_vec(),
});

uniffi::custom_type!(VaultKey, Vec<u8>, {
    remote,
    try_lift: |bytes| {
        VaultKey::try_from_bytes(&bytes)
            .map_err(|e| uniffi::deps::anyhow::anyhow!("{e:?}"))
    },
    lower: |key| key.as_bytes().to_vec(),
});

#[uniffi::remote(Record)]
pub struct Account {
    pub id: UserId,
    pub ark: AccountRootKey,
}

#[uniffi::remote(Record)]
pub struct Vault {
    pub id: VaultId,
    pub vault_key: VaultKey,
}

#[uniffi::remote(Record)]
pub struct CreateAccount {
    pub account: Account,
    pub default_vault: Vault,
}

#[derive(uniffi::Object)]
pub struct AccountManager;

#[uniffi::export]
impl AccountManager {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn create_account(&self) -> CreateAccount {
        CreateAccount::generate_new()
    }
}
