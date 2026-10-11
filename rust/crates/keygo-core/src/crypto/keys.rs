//! Key hierarchy and the AAD of each wrap:
//!
//! ```text
//! RootKEK --UserId--> AccountRootKey --VaultId--> VaultKey --ItemAad--> ItemKey --[u8]--> item data
//! ```

use crate::crypto::kdf::argon2::{Argon2Params, derive_argon2id};
use crate::crypto::{
    CipherKey, CryptoResult, Encrypts, ItemId, SymmetricKey, UserId, VaultId, Wraps, symmetric_key,
};
use aead::Generate;
use serde::Serialize;

/// Part of every password KEK's salt; must never change.
const PASSWORD_DOMAIN: &[u8] = b"v1:kek/pwd";

symmetric_key! {
    pub struct RootKEK;
}

symmetric_key! {
    pub struct AccountRootKey;
}

symmetric_key! {
    pub struct VaultKey;
}

symmetric_key! {
    pub struct ItemKey;
}

#[derive(Serialize)]
pub struct ItemAad {
    pub item_id: ItemId,
    pub vault_id: VaultId,
}

impl Wraps<AccountRootKey> for RootKEK {
    type Aad = UserId; // TODO: bind the credential epoch
}

impl Wraps<VaultKey> for AccountRootKey {
    type Aad = VaultId; // TODO: bind vault_key_epoch
}

impl Wraps<ItemKey> for VaultKey {
    type Aad = ItemAad;
}

impl Encrypts for ItemKey {
    type Aad = [u8];
}

impl RootKEK {
    pub fn from_password(password: &[u8], salt: &[u8]) -> CryptoResult<Self> {
        let derived = derive_argon2id(password, salt, PASSWORD_DOMAIN, Argon2Params::default())?;
        Self::from_bytes(derived.as_slice())
    }
}

macro_rules! impl_generate {
    ($($key:ident),+) => {
        $(impl $key {
            pub fn generate() -> Self {
                Self(CipherKey::generate())
            }
        })+
    };
}

impl_generate!(AccountRootKey, VaultKey, ItemKey);

#[cfg(test)]
mod tests {
    use super::*;

    const SALT: &[u8] = &[6; 16];

    #[test]
    fn same_password_and_salt_give_the_same_kek() {
        let a = RootKEK::from_password(b"hunter2", SALT).unwrap();
        let b = RootKEK::from_password(b"hunter2", SALT).unwrap();
        assert_eq!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn different_salts_give_different_keks() {
        let a = RootKEK::from_password(b"hunter2", &[7; 16]).unwrap();
        let b = RootKEK::from_password(b"hunter2", SALT).unwrap();
        assert_ne!(a.as_bytes(), b.as_bytes());
    }

    /// Pins `PASSWORD_DOMAIN` and the Argon2 profile; drift locks out every account.
    #[test]
    fn password_domain_is_pinned() {
        assert_eq!(PASSWORD_DOMAIN, b"v1:kek/pwd");

        let kek = RootKEK::from_password(b"hunter2", &[7; 16]).unwrap();

        assert_eq!(
            kek.as_bytes(),
            &[
                243, 77, 26, 134, 177, 95, 102, 67, 54, 167, 232, 38, 115, 170, 132, 28, 98, 29,
                146, 108, 157, 245, 225, 131, 93, 9, 236, 235, 207, 6, 219, 103,
            ][..]
        );
    }

    #[test]
    fn generated_keys_differ() {
        assert_ne!(
            VaultKey::generate().as_bytes(),
            VaultKey::generate().as_bytes()
        );
    }
}
