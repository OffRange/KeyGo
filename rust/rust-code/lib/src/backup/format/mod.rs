//! Backup serialization formats and the single entry point that dispatches to
//! them.
//!
//! Adding a format (e.g. CSV) is three local steps:
//!   1. add a `csv` sibling module that exposes `export`/`import`, reusing
//!      [`crate::backup::model`] (the data), [`crate::backup::encryption`] (the
//!      crypto), and [`crate::backup::BackupError`];
//!   2. add a variant to [`BackupFormat`];
//!   3. add the matching arm to `export`/`import` below.

pub mod json;

use crate::backup::encryption::BackupCredential;
use crate::backup::{Backup, BackupError};

/// The serialization format of a backup file.
#[derive(Clone, Copy, PartialEq, Eq)]
pub enum BackupFormat {
    Json,
}

impl BackupFormat {
    /// Serializes `backup` in this format, encrypting it when `cred` is `Some`.
    pub fn export(
        self,
        backup: &Backup,
        cred: Option<BackupCredential<'_>>,
    ) -> Result<String, BackupError> {
        match self {
            BackupFormat::Json => json::export(backup, cred),
        }
    }

    /// Parses a backup previously produced by [`export`](Self::export) in this format.
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
