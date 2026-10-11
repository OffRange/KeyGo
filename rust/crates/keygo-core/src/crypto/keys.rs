//! Key hierarchy and the AAD of each wrap:
//!
//! ```text
//! RootKEK --UserId--> AccountRootKey --VaultId--> VaultKey --ItemAad--> ItemKey --ItemDataAad--> item data
//! ```

use crate::crypto::kdf::argon2::{Argon2Params, derive_argon2id};
use crate::crypto::{
    Aad, CipherKey, CryptoResult, Encrypts, ItemId, SymmetricKey, UserId, VaultId, Wraps, aad,
    symmetric_key,
};
use aead::Generate;
use serde::{Serialize, Serializer};

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

aad! {
    pub struct ItemAad {
        pub item_id: ItemId,
        pub vault_id: VaultId,
    }
}

/// The vault is left out on purpose: the item key's wrap binds it, so moving an item between
/// vaults rewraps only the item key.
pub struct ItemDataAad {
    pub item_id: ItemId,
    pub kind: SecretKind,
}

/// One byte string, `item_id || label`: the layout 2.1.0 shipped, when callers built it by hand.
impl Serialize for ItemDataAad {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        let mut bytes = self.item_id.0.as_bytes().to_vec();
        bytes.extend_from_slice(self.kind.label().as_bytes());
        serializer.serialize_bytes(&bytes)
    }
}

impl Aad for ItemDataAad {}

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum SecretKind {
    Password,
    CardNumber,
    Cvv,
    PasskeyPrivateKey,
    TotpSecret,
    PasswordHealthReport,
}

impl SecretKind {
    /// Part of every item-data AAD; must never change.
    const fn label(self) -> &'static str {
        match self {
            Self::Password => "password",
            Self::CardNumber => "credit_card_number",
            Self::Cvv => "credit_card_cvv",
            Self::PasskeyPrivateKey => "passkey_private_key",
            Self::TotpSecret => "totp_secret",
            Self::PasswordHealthReport => "password_health_report",
        }
    }
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
    type Aad = ItemDataAad;
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

    /// Pins every label; drift makes that field undecryptable everywhere.
    #[test]
    fn secret_kind_labels_are_pinned() {
        let labels = [
            SecretKind::Password,
            SecretKind::CardNumber,
            SecretKind::Cvv,
            SecretKind::PasskeyPrivateKey,
            SecretKind::TotpSecret,
            SecretKind::PasswordHealthReport,
        ]
        .map(SecretKind::label);

        assert_eq!(
            labels,
            [
                "password",
                "credit_card_number",
                "credit_card_cvv",
                "passkey_private_key",
                "totp_secret",
                "password_health_report",
            ]
        );
    }

    #[test]
    fn item_data_aad_encodes_like_the_hand_built_bytes() {
        let item_id = ItemId::new_v4();
        let mut hand_built = item_id.0.as_bytes().to_vec();
        hand_built.extend_from_slice(b"totp_secret");

        let aad = ItemDataAad {
            item_id,
            kind: SecretKind::TotpSecret,
        };

        assert_eq!(
            bcs::to_bytes(&aad).unwrap(),
            bcs::to_bytes(&hand_built).unwrap()
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
