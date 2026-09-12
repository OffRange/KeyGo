use crate::crypto::error::{CryptoError, CryptoResult};
use hkdf::Hkdf;
use sha2::Sha256;

/// Length of the derived key material (bytes).
pub const DERIVED_KEY_LEN: usize = 32;

/// Derive `DERIVED_KEY_LEN` bytes from high-entropy keying material using
/// HKDF-SHA256 (RFC 5869).
///
/// Unlike [`super::argon2::derive_argon2id`], this performs no memory-hard
/// stretch: it is for inputs that are already uniformly random (e.g. the ARK),
/// where a password KDF would add cost without security. `salt` is the HKDF
/// extract salt (a fresh per-use random value); `info` is the HKDF expand label
/// used to domain-separate otherwise-identical derivations.
///
/// HKDF accepts any salt length (including empty), so - unlike the Argon2id
/// primitive - no minimum-salt check is enforced here.
pub(crate) fn derive_hkdf_sha256(
    ikm: &[u8],
    salt: &[u8],
    info: &[u8],
) -> CryptoResult<[u8; DERIVED_KEY_LEN]> {
    let hk = Hkdf::<Sha256>::new(Some(salt), ikm);
    let mut out = [0u8; DERIVED_KEY_LEN];
    hk.expand(info, &mut out)
        .map_err(|e| CryptoError::KdfError(e.to_string()))?;
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    const IKM: &[u8] = &[7u8; 32];
    const SALT: &[u8] = &[0x11; 16];

    #[test]
    fn deterministic_for_same_inputs() {
        let a = derive_hkdf_sha256(IKM, SALT, b"info").unwrap();
        let b = derive_hkdf_sha256(IKM, SALT, b"info").unwrap();
        assert_eq!(a, b);
    }

    #[test]
    fn different_salt_different_key() {
        let a = derive_hkdf_sha256(IKM, SALT, b"info").unwrap();
        let b = derive_hkdf_sha256(IKM, &[0x22; 16], b"info").unwrap();
        assert_ne!(a, b);
    }

    #[test]
    fn different_info_different_key() {
        let a = derive_hkdf_sha256(IKM, SALT, b"info-a").unwrap();
        let b = derive_hkdf_sha256(IKM, SALT, b"info-b").unwrap();
        assert_ne!(a, b);
    }

    #[test]
    fn different_ikm_different_key() {
        let a = derive_hkdf_sha256(IKM, SALT, b"info").unwrap();
        let b = derive_hkdf_sha256(&[8u8; 32], SALT, b"info").unwrap();
        assert_ne!(a, b);
    }
}
