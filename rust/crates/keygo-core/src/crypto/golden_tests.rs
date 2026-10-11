//! Shipped ciphertexts under fixed keys. They must keep opening; regenerate only with a migration.

use crate::crypto::{
    AccountRootKey, Encrypts, ItemAad, ItemDataAad, ItemId, ItemKey, RootKEK, Sealed, SecretKind,
    SymmetricKey, UserId, VaultId, VaultKey, Wraps,
};
use uuid::Uuid;

const USER_ID: UserId = UserId(Uuid::from_u128(0x1111_1111_1111_4111_8111_1111_1111_1111));
const VAULT_ID: VaultId = VaultId(Uuid::from_u128(0x2222_2222_2222_4222_8222_2222_2222_2222));
const ITEM_ID: ItemId = ItemId(Uuid::from_u128(0x3333_3333_3333_4333_8333_3333_3333_3333));

const KEK: [u8; 32] = [4; 32];
const ARK: [u8; 32] = [1; 32];
const VAULT_KEY: [u8; 32] = [2; 32];
const ITEM_KEY: [u8; 32] = [3; 32];

const WRAPPED_ARK: (&str, &str) = (
    "U/SS+Svk6Yv/KYpM",
    "Y5MbYTqChWfQdtQGMsql2ktBdsR8/fpaSNV5rL46+4Fplqw1uE2wvIaeFnDMGoF+",
);
const WRAPPED_VAULT_KEY: (&str, &str) = (
    "zBY65MRmoCKkpnqw",
    "A71Cm/YeTWJcwR7jTPZ5STYXH6ivVDkxGBKqh4EYgFaUBUFX5785SDcIXLh9A9j6",
);
const WRAPPED_ITEM_KEY: (&str, &str) = (
    "JynhAutrXQRAHS1c",
    "D58m240ktQyE5IheW9sB4zFcdZY0T7zQ33qzrIWY9Iq6HkhM6weLprmSPQ2//RbA",
);
/// Sealed by the 2.1.0 path, with the AAD built by hand as `ITEM_ID || "password"`.
const ITEM_DATA: (&str, &str) = (
    "EPAic05x44CkZoRK",
    "OPtIsA8vfGNIVOsCp2wM+wtEQFJkhvA5X4v+js1gKio=",
);
const ITEM_DATA_PLAINTEXT: &[u8] = b"secret item data";

fn sealed<T: ?Sized>((nonce, ciphertext): (&str, &str)) -> Sealed<T> {
    Sealed::from_parts(
        &crate::b64::decode(nonce).unwrap(),
        crate::b64::decode(ciphertext).unwrap(),
    )
    .unwrap()
}

#[test]
fn password_wrapped_ark_still_unwraps() {
    let kek = RootKEK::from_bytes(&KEK).unwrap();
    let ark = kek.unwrap_key(&sealed(WRAPPED_ARK), &USER_ID).unwrap();
    assert_eq!(ark.as_bytes(), ARK);
}

#[test]
fn vault_key_still_unwraps() {
    let ark = AccountRootKey::from_bytes(&ARK).unwrap();
    let vault_key = ark
        .unwrap_key(&sealed(WRAPPED_VAULT_KEY), &VAULT_ID)
        .unwrap();
    assert_eq!(vault_key.as_bytes(), VAULT_KEY);
}

#[test]
fn item_key_still_unwraps() {
    let vault_key = VaultKey::from_bytes(&VAULT_KEY).unwrap();
    let aad = ItemAad {
        item_id: ITEM_ID,
        vault_id: VAULT_ID,
    };
    let item_key = vault_key
        .unwrap_key(&sealed(WRAPPED_ITEM_KEY), &aad)
        .unwrap();
    assert_eq!(item_key.as_bytes(), ITEM_KEY);
}

#[test]
fn item_data_still_decrypts() {
    let item_key = ItemKey::from_bytes(&ITEM_KEY).unwrap();
    let aad = ItemDataAad {
        item_id: ITEM_ID,
        kind: SecretKind::Password,
    };
    let plaintext = item_key.decrypt(&sealed(ITEM_DATA), &aad).unwrap();
    assert_eq!(plaintext, ITEM_DATA_PLAINTEXT);
}
