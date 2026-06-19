pub mod error;
pub mod json;

pub use error::BackupError;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Backup {
    pub vaults: Vec<Vault>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Vault {
    pub name: String,
    pub login: Login,
    pub card: Card,
}

macro_rules! backup_item {
    (
        $(#[$meta:meta])*
        $vis:vis struct $name:ident {
            $(
                $(#[$field_meta:meta])*
                $field_vis:vis $field:ident : $field_ty:ty
            ),*$(,)?
        }
    ) => {
        $(#[$meta])*

        #[derive(Debug, Clone, Serialize, Deserialize)]
        $vis struct $name {
            pub title: String,
            pub notes: Option<String>,
            pub tags: Vec<String>,
            pub pinned: bool,
            $(
                $(#[$field_meta])*
                $field_vis $field: $field_ty,
            )*
        }
    };
}

backup_item! {
    pub struct Login {
        pub username: Option<String>,
        pub password: Option<String>,
        pub totp_secret: Option<String>,
        pub website: Option<String>,
        pub passkey: Option<Passkey>,
   }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Passkey {
    pub user_name: String,
    pub user_display_name: String,
    pub credential_id: Vec<u8>,
    pub private_key: Vec<u8>,
    pub rp: String,
}

backup_item! {
    pub struct Card {
        pub cardholder: Option<String>,
        pub number: String,
        pub expiration_month: Option<u8>,
        pub expiration_year: Option<u16>,
        pub cvv: Option<String>,
    }
}

use crate::crypto::error::CryptoResult;
use crate::crypto::key::KeyMaterial;
use crate::crypto::keys::AccountRootKey;
use crate::crypto::primitive::argon2::derive_argon2id;
use crate::define_aead_key;
use aes_gcm_siv::Aes256GcmSiv;

const DOMAIN_PASSPHRASE: &[u8] = b"v1:backup/passphrase";
const DOMAIN_ARK: &[u8] = b"v1:backup/ark";

define_aead_key! {
    pub struct BackupKey(Aes256GcmSiv);
}

impl BackupKey {
    pub(crate) fn from_passphrase(passphrase: &[u8], salt: &[u8]) -> CryptoResult<Self> {
        let derived = derive_argon2id(passphrase, salt, DOMAIN_PASSPHRASE)?;
        Self::try_from_bytes(&derived)
    }

    pub(crate) fn from_ark(ark: &AccountRootKey, salt: &[u8]) -> CryptoResult<Self> {
        let derived = derive_argon2id(ark.as_bytes(), salt, DOMAIN_ARK)?;
        Self::try_from_bytes(&derived)
    }
}

#[cfg(test)]
mod backup_key_tests {
    use super::*;
    use crate::crypto::key::KeyMaterial;
    use crate::crypto::keys::AccountRootKey;

    const SALT: &[u8] = &[3u8; 16];

    #[test]
    fn passphrase_key_is_deterministic() {
        let a = BackupKey::from_passphrase(b"hunter2", SALT).unwrap();
        let b = BackupKey::from_passphrase(b"hunter2", SALT).unwrap();
        assert_eq!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn passphrase_key_changes_with_salt() {
        let a = BackupKey::from_passphrase(b"hunter2", SALT).unwrap();
        let b = BackupKey::from_passphrase(b"hunter2", &[9u8; 16]).unwrap();
        assert_ne!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn ark_key_is_deterministic() {
        let ark = AccountRootKey::try_from_bytes(&[7u8; 32]).unwrap();
        let a = BackupKey::from_ark(&ark, SALT).unwrap();
        let b = BackupKey::from_ark(&ark, SALT).unwrap();
        assert_eq!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn passphrase_and_ark_paths_are_domain_separated() {
        let raw = [7u8; 32];
        let ark = AccountRootKey::try_from_bytes(&raw).unwrap();
        let pass = BackupKey::from_passphrase(&raw, SALT).unwrap();
        let ark_key = BackupKey::from_ark(&ark, SALT).unwrap();
        assert_ne!(pass.as_bytes(), ark_key.as_bytes());
    }
}
