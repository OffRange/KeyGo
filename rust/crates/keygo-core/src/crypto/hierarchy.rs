//! Key hierarchy and the AAD of each wrap:
//!
//! ```text
//! RootKek --UserId--> AccountRootKey --VaultId--> VaultKey --ItemAad--> ItemKey --ItemDataAad--> item data
//!                                    \
//!                                     --NamespaceKeyAad--> NamespaceKey --NamespaceDataAad--> account record
//! ```

use crate::crypto::aad::{Aad, aad};
use crate::crypto::kdf::argon2::{Argon2Params, derive_argon2id};
use crate::crypto::{CipherKey, Encrypts, KeyMaterial, Wraps};
use crate::keys::ServerKek;
use crate::{
    AccountRootKey, CryptoResult, ItemId, ItemKey, NamespaceId, NamespaceKey, RootKek, UserId,
    VaultId, VaultKey,
};
use aead::Generate;
use hkdf::Hkdf;
use serde::{Serialize, Serializer};
use sha2::Sha256;
use zeroize::Zeroizing;

/// Part of every password KEK's salt; must never change.
const PASSWORD_DOMAIN: &[u8] = b"v1:kek/pwd";

/// The HKDF info of every server KEK; must never change.
const OPAQUE_KEK_DOMAIN: &[u8] = b"v1:kek/opaque";

/// Part of every namespace-key wrap AAD; must never change.
const NAMESPACE_KEY_DOMAIN: &str = "v1:ns/key";

/// Part of every account-record AAD; must never change.
const NAMESPACE_DATA_DOMAIN: &str = "v1:ns/data";

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

pub struct NamespaceKeyAad {
    pub namespace: NamespaceId,
}

impl Serialize for NamespaceKeyAad {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        (NAMESPACE_KEY_DOMAIN, self.namespace).serialize(serializer)
    }
}

impl Aad for NamespaceKeyAad {}

pub struct NamespaceDataAad {
    pub namespace: NamespaceId,
    pub kind: SecretKind,
}

impl Serialize for NamespaceDataAad {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        (NAMESPACE_DATA_DOMAIN, self.namespace, self.kind.label()).serialize(serializer)
    }
}

impl Aad for NamespaceDataAad {}

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

macro_rules! wraps {
    ($($parent:ident -> $child:ident with $aad:ident),+ $(,)?) => {
        $(
            impl Wraps<$child> for $parent {
                type Aad = $aad;
            }
        )+
    };
}

wraps! {
    ServerKek      -> AccountRootKey with UserId,
    RootKek        -> AccountRootKey with UserId, // TODO: bind the credential epoch
    AccountRootKey -> VaultKey       with VaultId, // TODO: bind vault_key_epoch
    AccountRootKey -> NamespaceKey   with NamespaceKeyAad,
    VaultKey       -> ItemKey        with ItemAad,
}

impl Encrypts for ItemKey {
    type Aad = ItemDataAad;
}

impl Encrypts for NamespaceKey {
    type Aad = NamespaceDataAad;
}

impl RootKek {
    pub fn from_password(password: &[u8], salt: &[u8]) -> CryptoResult<Self> {
        let derived = derive_argon2id(password, salt, PASSWORD_DOMAIN, Argon2Params::default())?;
        Self::from_bytes(derived.as_slice())
    }
}

impl ServerKek {
    pub fn from_export_key(key: &[u8], user_id: UserId) -> CryptoResult<Self> {
        let mut derived = Zeroizing::new([0u8; 32]);
        Hkdf::<Sha256>::new(Some(user_id.as_uuid().as_bytes()), key)
            .expand(OPAQUE_KEK_DOMAIN, derived.as_mut())
            .expect("32 bytes is a valid HKDF-SHA256 output length");
        Self::from_bytes(derived.as_slice())
    }
}

macro_rules! impl_generate {
    ($($key:ident),+) => {
        $(impl $key {
            pub fn generate() -> Self {
                Self { key: CipherKey::generate() }
            }
        })+
    };
}

impl_generate!(AccountRootKey, VaultKey, ItemKey, NamespaceKey);

#[cfg(test)]
mod tests {
    use super::*;
    use crate::Sealed;

    const SALT: &[u8] = &[6; 16];

    #[test]
    fn same_password_and_salt_give_the_same_kek() {
        let a = RootKek::from_password(b"hunter2", SALT).unwrap();
        let b = RootKek::from_password(b"hunter2", SALT).unwrap();
        assert_eq!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn different_salts_give_different_keks() {
        let a = RootKek::from_password(b"hunter2", &[7; 16]).unwrap();
        let b = RootKek::from_password(b"hunter2", SALT).unwrap();
        assert_ne!(a.as_bytes(), b.as_bytes());
    }

    /// Pins `PASSWORD_DOMAIN` and the Argon2 profile; drift locks out every account.
    #[test]
    fn password_domain_is_pinned() {
        assert_eq!(PASSWORD_DOMAIN, b"v1:kek/pwd");

        let kek = RootKek::from_password(b"hunter2", &[7; 16]).unwrap();

        assert_eq!(
            kek.as_bytes(),
            &[
                243, 77, 26, 134, 177, 95, 102, 67, 54, 167, 232, 38, 115, 170, 132, 28, 98, 29,
                146, 108, 157, 245, 225, 131, 93, 9, 236, 235, 207, 6, 219, 103,
            ][..]
        );
    }

    fn user(byte: u8) -> UserId {
        UserId::from(uuid::Uuid::from_bytes([byte; 16]))
    }

    /// Pins `OPAQUE_KEK_DOMAIN` and the salt; drift makes every server-held ARK unopenable.
    #[test]
    fn opaque_kek_domain_is_pinned() {
        assert_eq!(OPAQUE_KEK_DOMAIN, b"v1:kek/opaque");

        let kek = ServerKek::from_export_key(&[9; 64], user(3)).unwrap();

        // Cross-checked against RFC 5869 HKDF-SHA256 outside Rust.
        assert_eq!(
            kek.as_bytes(),
            &[
                27, 101, 7, 214, 170, 93, 0, 161, 91, 141, 171, 209, 225, 137, 214, 134, 221, 88,
                108, 144, 212, 175, 208, 136, 181, 194, 182, 0, 55, 168, 52, 233,
            ][..]
        );
    }

    #[test]
    fn server_kek_depends_on_the_user() {
        let a = ServerKek::from_export_key(&[9; 64], user(3)).unwrap();
        let b = ServerKek::from_export_key(&[9; 64], user(4)).unwrap();
        assert_ne!(a.as_bytes(), b.as_bytes());
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
        let item_id = ItemId::random();
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

    const NAMESPACE: NamespaceId = NamespaceId(uuid::Uuid::from_u128(1));

    /// Pins both namespace AAD layouts; drift makes every account record unreadable.
    #[test]
    fn namespace_aads_are_pinned() {
        let id = NAMESPACE.0.as_bytes();
        let mut key_aad = vec![9];
        key_aad.extend_from_slice(b"v1:ns/key");
        key_aad.push(16);
        key_aad.extend_from_slice(id);

        let mut data_aad = vec![10];
        data_aad.extend_from_slice(b"v1:ns/data");
        data_aad.push(16);
        data_aad.extend_from_slice(id);
        data_aad.push(22);
        data_aad.extend_from_slice(b"password_health_report");

        assert_eq!(
            bcs::to_bytes(&NamespaceKeyAad {
                namespace: NAMESPACE
            })
            .unwrap(),
            key_aad
        );
        assert_eq!(
            bcs::to_bytes(&NamespaceDataAad {
                namespace: NAMESPACE,
                kind: SecretKind::PasswordHealthReport,
            })
            .unwrap(),
            data_aad
        );
    }

    #[test]
    fn a_wrapped_vault_key_never_unwraps_as_a_namespace_key() {
        let ark = AccountRootKey::generate();
        let wrapped = ark
            .wrap_key(&VaultKey::generate(), &VaultId(NAMESPACE.0))
            .unwrap();
        let as_namespace_key =
            Sealed::<NamespaceKey>::from_parts(wrapped.nonce(), wrapped.ciphertext().to_vec())
                .unwrap();

        assert!(
            ark.unwrap_key(
                &as_namespace_key,
                &NamespaceKeyAad {
                    namespace: NAMESPACE
                }
            )
            .is_err()
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
