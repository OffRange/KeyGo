use lib::crypto::VaultKey;
use std::sync::Arc;

#[derive(uniffi::Object)]
pub struct VaultManager;

#[uniffi::export]
impl VaultManager {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn create_new_vault_key(&self) -> VaultKey {
        VaultKey::generate_random()
    }
}
