use crate::backup::{Backup, BackupKey, Vault};

impl Backup {
    pub fn new(vaults: Vec<Vault>) -> Self {
        Self { vaults }
    }

    pub fn as_json(&self, key: Option<BackupKey>) -> String {
        serde_json::to_string(self).unwrap()
    }
}