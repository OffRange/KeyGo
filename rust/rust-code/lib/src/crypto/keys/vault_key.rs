use crate::crypto::keys::account_root_key::AccountRootKey;
use crate::crypto::types::VaultId;
use crate::{define_aead_key, define_wrap};
use aes_gcm_siv::Aes256GcmSiv;

define_aead_key! {
    random pub struct VaultKey(Aes256GcmSiv);
}

define_wrap!(AccountRootKey => VaultKey, aad = VaultId); //TODO: also include epoch: vault_key_epoch
