use crate::crypto::VaultKey;
use crate::crypto::types::VaultId;

pub struct Vault {
    pub id: VaultId,
    pub vault_key: VaultKey,
}

impl Vault {
    pub fn generate_new() -> Self {
        Self {
            id: VaultId::new_v4(),
            vault_key: VaultKey::generate_random(),
        }
    }
}
