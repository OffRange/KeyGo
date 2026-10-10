use crate::define_aead_key;
use aes_gcm_siv::Aes256GcmSiv;

define_aead_key! {
    random pub struct AccountRootKey(Aes256GcmSiv);
}
