use crate::crypto::Encrypts;
use crate::crypto::KeyMaterial;
use crate::crypto::aad::aad;
use crate::crypto::kdf::argon2::{Argon2Params, derive_argon2id};
use crate::crypto::kdf::hkdf::derive_hkdf_sha256;
use crate::{AccountRootKey, BackupKey, CryptoResult};
use serde::{Deserialize, Serialize};

const DOMAIN_PASSPHRASE: &[u8] = b"v1:backup/passphrase";
const DOMAIN_ARK: &[u8] = b"v1:backup/ark";

#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, Debug)]
#[serde(rename_all = "snake_case")]
pub enum KeySource {
    Passphrase,
    Ark,
}

aad! {
    /// Wire format: change only with a version bump.
    pub struct BackupAad {
        pub version: u32,
        pub source: KeySource,
    }
}

impl Encrypts for BackupKey {
    type Aad = BackupAad;
}

impl BackupKey {
    pub fn from_passphrase(
        passphrase: &[u8],
        salt: &[u8],
        params: Argon2Params,
    ) -> CryptoResult<Self> {
        let derived = derive_argon2id(passphrase, salt, DOMAIN_PASSPHRASE, params)?;
        Self::from_bytes(derived.as_slice())
    }

    pub fn from_ark(ark: &AccountRootKey, salt: &[u8]) -> CryptoResult<Self> {
        Self::from_bytes(derive_hkdf_sha256(ark.as_bytes(), salt, DOMAIN_ARK).as_slice())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const SALT: &[u8] = &[3u8; 16];

    #[test]
    fn passphrase_key_is_deterministic() {
        let a = BackupKey::from_passphrase(b"hunter2", SALT, Argon2Params::default()).unwrap();
        let b = BackupKey::from_passphrase(b"hunter2", SALT, Argon2Params::default()).unwrap();
        assert_eq!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn passphrase_key_changes_with_salt() {
        let a = BackupKey::from_passphrase(b"hunter2", SALT, Argon2Params::default()).unwrap();
        let b =
            BackupKey::from_passphrase(b"hunter2", &[9u8; 16], Argon2Params::default()).unwrap();
        assert_ne!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn passphrase_key_changes_with_params() {
        let a = BackupKey::from_passphrase(b"hunter2", SALT, Argon2Params::default()).unwrap();
        let b = BackupKey::from_passphrase(
            b"hunter2",
            SALT,
            Argon2Params {
                iters: Argon2Params::default().iters + 1,
                ..Argon2Params::default()
            },
        )
        .unwrap();
        assert_ne!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn ark_key_is_deterministic() {
        let ark = AccountRootKey::from_bytes(&[7u8; 32]).unwrap();
        let a = BackupKey::from_ark(&ark, SALT).unwrap();
        let b = BackupKey::from_ark(&ark, SALT).unwrap();
        assert_eq!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn passphrase_and_ark_paths_are_domain_separated() {
        let raw = [7u8; 32];
        let ark = AccountRootKey::from_bytes(&raw).unwrap();
        let pass = BackupKey::from_passphrase(&raw, SALT, Argon2Params::default()).unwrap();
        let ark_key = BackupKey::from_ark(&ark, SALT).unwrap();
        assert_ne!(pass.as_bytes(), ark_key.as_bytes());
    }
}
