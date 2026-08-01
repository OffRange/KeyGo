use crate::crypto::TryDeriveFrom;
use crate::crypto::error::CryptoResult;
use crate::crypto::key::KeyMaterial;
use crate::crypto::keys::account_root_key::AccountRootKey;
use crate::crypto::primitive::argon2::derive_argon2id;
use crate::crypto::types::UserId;
use crate::{define_aead_key, define_wrap};
use aes_gcm_siv::Aes256GcmSiv;

define_aead_key! {
    /// ### Root Key Encryption Key
    ///
    /// Used to wrap the [AccountRootKey] (ARK). Derived on every login from either the user's
    /// password or the user's recovery key via Argon2id. Never stored directly.
    pub struct RootKEK(Aes256GcmSiv);
}

define_wrap!(RootKEK => AccountRootKey, aad = UserId); // TODO: also include epoch: pw wrapped: pwd_cred_epoch, rk wrapped: rk_cred_epoch

impl<'a> TryDeriveFrom<&'a [u8]> for RootKEK {
    fn try_derive_from(source: &'a [u8], salt: &[u8], domain: &[u8]) -> CryptoResult<Self> {
        let derived = derive_argon2id(source, salt, domain)?;
        <Self as KeyMaterial>::try_from_bytes(&derived)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const SALT: &[u8] = &[6; 16];
    const DOMAIN: &[u8] = b"v1:kek/pwd";

    #[test]
    fn same_credential_same_password_same_key() {
        let a = RootKEK::try_derive_from(b"hunter2", SALT, DOMAIN).unwrap();
        let b = RootKEK::try_derive_from(b"hunter2", SALT, DOMAIN).unwrap();
        assert_eq!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn different_salt_different_keys() {
        let a = RootKEK::try_derive_from(b"hunter2", &[7; 16], DOMAIN).unwrap();
        let b = RootKEK::try_derive_from(b"hunter2", SALT, DOMAIN).unwrap();
        assert_ne!(a.as_bytes(), b.as_bytes());
    }

    #[test]
    fn different_domain_different_keys() {
        let a = RootKEK::try_derive_from(b"hunter2", SALT, DOMAIN).unwrap();
        let b = RootKEK::try_derive_from(b"hunter2", SALT, b"v2:kek/pwd").unwrap();
        assert_ne!(a.as_bytes(), b.as_bytes());
    }
}
