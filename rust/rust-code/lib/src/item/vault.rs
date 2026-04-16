use crate::crypto::types::VaultId;
use crate::crypto::VaultKey;

pub struct Vault {
    pub id: VaultId,
    pub vault_key: VaultKey,
}

impl Vault {
    pub fn new() -> Self {
        Self {
            id: VaultId::new_v4(),
            vault_key: VaultKey::generate_random(),
        }
    }
}