use crate::crypto::VaultKey;
use crate::crypto::primitive::aead_data::AeadEncryptor;
use crate::crypto::types::{ItemId, VaultId};
use crate::{define_aead_key, define_wrap};
use aes_gcm_siv::Aes256GcmSiv;
use serde::Serialize;

define_aead_key! {
    random pub struct ItemKey(Aes256GcmSiv);
}

define_wrap!(VaultKey => ItemKey, aad = ItemId);

#[derive(Serialize)]
pub struct ItemAad {
    pub item_id: ItemId,
    pub vault_id: VaultId,
}

impl AeadEncryptor for ItemKey {
    type Aad = ItemAad;
}
