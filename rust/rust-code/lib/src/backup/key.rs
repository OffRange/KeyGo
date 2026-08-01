use crate::crypto::error::CryptoResult;
use crate::crypto::key::KeyMaterial;
use crate::crypto::keys::AccountRootKey;
use crate::crypto::primitive::argon2::{Argon2Params, derive_argon2id_with_params};
use crate::crypto::primitive::hkdf::derive_hkdf_sha256;
use crate::define_aead_key;
use aes_gcm_siv::Aes256GcmSiv;

const DOMAIN_PASSPHRASE: &[u8] = b"v1:backup/passphrase";
const DOMAIN_ARK: &[u8] = b"v1:backup/ark";

define_aead_key! {
    pub struct BackupKey(Aes256GcmSiv);
}

impl BackupKey {
    pub(crate) fn from_passphrase(
        passphrase: &[u8],
        salt: &[u8],
        params: Argon2Params,
    ) -> CryptoResult<Self> {
        let derived = derive_argon2id_with_params(passphrase, salt, DOMAIN_PASSPHRASE, params)?;
        Self::try_from_bytes(&derived)
    }

    pub(crate) fn from_ark(ark: &AccountRootKey, salt: &[u8]) -> CryptoResult<Self> {
        let derived = derive_hkdf_sha256(ark.as_bytes(), salt, DOMAIN_ARK)?;
        Self::try_from_bytes(&derived)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::crypto::key::KeyMaterial;
    use crate::crypto::keys::AccountRootKey;

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
        let ark = AccountRootKey::try_from_bytes(&[7u8; 32]).unwrap();
        let a = BackupKey::from_ark(&ark, SALT).unwrap();
        let b = BackupKey::from_ark(&ark, SALT).unwrap();
        assert_eq!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn passphrase_and_ark_paths_are_domain_separated() {
        let raw = [7u8; 32];
        let ark = AccountRootKey::try_from_bytes(&raw).unwrap();
        let pass = BackupKey::from_passphrase(&raw, SALT, Argon2Params::default()).unwrap();
        let ark_key = BackupKey::from_ark(&ark, SALT).unwrap();
        assert_ne!(pass.as_bytes(), ark_key.as_bytes());
    }
}
