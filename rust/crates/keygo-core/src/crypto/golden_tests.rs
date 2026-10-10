//! Ciphertexts produced when the key hierarchy shipped, under fixed keys and ids. Every vault in
//! the field holds blobs of exactly this shape, so each one must keep opening: a change to the
//! cipher, the nonce layout, or the BCS encoding of an AAD fails here instead of on a device.
//! Regenerate only alongside a deliberate, migrated format change.

use crate::crypto::{
    AccountRootKey, Encrypts, ItemAad, ItemKey, RootKEK, Sealed, SymmetricKey, VaultKey, Wraps,
};
use uuid::Uuid;

const USER_ID: Uuid = Uuid::from_u128(0x1111_1111_1111_4111_8111_1111_1111_1111);
const VAULT_ID: Uuid = Uuid::from_u128(0x2222_2222_2222_4222_8222_2222_2222_2222);
const ITEM_ID: Uuid = Uuid::from_u128(0x3333_3333_3333_4333_8333_3333_3333_3333);

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
const ITEM_DATA: (&str, &str) = (
    "t78N42bK5A+RKqzk",
    "qD1SvmKQlBXgEE4MzPeJ3ZWJKduWgqkOU+xSRBxlBsY=",
);
const ITEM_DATA_AAD: &[u8] = b"item-aad";
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
    let plaintext = item_key.decrypt(&sealed(ITEM_DATA), ITEM_DATA_AAD).unwrap();
    assert_eq!(plaintext, ITEM_DATA_PLAINTEXT);
}
