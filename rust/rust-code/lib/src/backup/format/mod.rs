pub mod json;

use crate::backup::encryption::BackupCredential;
use crate::backup::{Backup, BackupError};

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum BackupFormat {
    Json,
}

impl BackupFormat {
    pub fn export(
        self,
        backup: &Backup,
        cred: Option<BackupCredential<'_>>,
    ) -> Result<String, BackupError> {
        match self {
            BackupFormat::Json => json::export(backup, cred),
        }
    }

    pub fn import(
        self,
        data: &str,
        cred: Option<BackupCredential<'_>>,
    ) -> Result<Backup, BackupError> {
        match self {
            BackupFormat::Json => json::import(data, cred),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn json_round_trips_through_dispatch() {
        let backup = Backup { vaults: vec![] };
        let data = BackupFormat::Json.export(&backup, None).unwrap();
        let restored = BackupFormat::Json.import(&data, None).unwrap();
        assert!(restored.vaults.is_empty());
    }
}
