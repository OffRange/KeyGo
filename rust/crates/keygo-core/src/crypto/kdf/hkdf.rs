use crate::crypto::KEY_LEN;
use hkdf::Hkdf;
use sha2::Sha256;
use zeroize::Zeroizing;

pub(crate) fn derive_hkdf_sha256(ikm: &[u8], salt: &[u8], info: &[u8]) -> Zeroizing<[u8; KEY_LEN]> {
    let mut out = Zeroizing::new([0u8; KEY_LEN]);
    Hkdf::<Sha256>::new(Some(salt), ikm)
        .expand(info, out.as_mut_slice())
        .expect("KEY_LEN is far below HKDF-SHA256's 8160 byte output limit");
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    const IKM: &[u8] = &[7u8; 32];
    const SALT: &[u8] = &[0x11; 16];

    #[test]
    fn deterministic_for_same_inputs() {
        assert_eq!(
            *derive_hkdf_sha256(IKM, SALT, b"info"),
            *derive_hkdf_sha256(IKM, SALT, b"info")
        );
    }

    #[test]
    fn different_salt_different_key() {
        assert_ne!(
            *derive_hkdf_sha256(IKM, SALT, b"info"),
            *derive_hkdf_sha256(IKM, &[0x22; 16], b"info")
        );
    }

    #[test]
    fn different_info_different_key() {
        assert_ne!(
            *derive_hkdf_sha256(IKM, SALT, b"info-a"),
            *derive_hkdf_sha256(IKM, SALT, b"info-b")
        );
    }

    #[test]
    fn different_ikm_different_key() {
        assert_ne!(
            *derive_hkdf_sha256(IKM, SALT, b"info"),
            *derive_hkdf_sha256(&[8u8; 32], SALT, b"info")
        );
    }
}
