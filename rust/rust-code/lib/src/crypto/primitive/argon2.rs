use crate::crypto::error::{CryptoError, CryptoResult};
use argon2::{Algorithm, Argon2, Params, Version};

/// Minimum salt length enforced for password-based derivation (RFC 9106 §3.1).
pub const MIN_SALT_LEN: usize = 16;

/// Length of the derived key material (bytes).
pub const DERIVED_KEY_LEN: usize = 32;

fn get_argon<'a>() -> Argon2<'a> {
    let params = Params::new(64 * 1024, 3, 4, Some(DERIVED_KEY_LEN)).expect("valid default params");
    Argon2::new(Algorithm::Argon2id, Version::V0x13, params)
}

/// Derive `DERIVED_KEY_LEN` bytes from a password using Argon2id.
///
/// The salt must be caller-provided, at least `MIN_SALT_LEN` bytes, and persisted with the
/// credential so the same KEK can be re-derived on later logins. `domain` is mixed into the salt
/// as a label to separate otherwise-identical derivations (e.g. password-KEK vs recovery-KEK).
///
/// The password is not zeroized by this function — the caller owns the password buffer and must
/// wrap it in `Zeroizing` / a secret type at the FFI boundary.
pub(crate) fn derive_argon2id(
    password: &[u8],
    salt: &[u8],
    domain: &[u8],
) -> CryptoResult<[u8; DERIVED_KEY_LEN]> {
    if password.is_empty() {
        return Err(CryptoError::KdfError("empty password".into()));
    }
    if salt.len() < MIN_SALT_LEN {
        return Err(CryptoError::KdfError(format!(
            "salt too short: {} < {}",
            salt.len(),
            MIN_SALT_LEN,
        )));
    }

    // Bind domain label into the salt so that changing that produces an independent key.
    let mut full_salt = Vec::with_capacity(salt.len() + domain.len());
    full_salt.extend_from_slice(salt);
    full_salt.extend_from_slice(domain);

    let argon2 = get_argon();

    let mut out = [0u8; DERIVED_KEY_LEN];
    argon2
        .hash_password_into(password, &full_salt, out.as_mut_slice())
        .map_err(|e| CryptoError::KdfError(e.to_string()))?;
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    const PW: &[u8] = b"correct horse battery staple";
    const SALT: [u8; 16] = [0x11; 16];

    #[test]
    fn deterministic_for_same_inputs() {
        let a = derive_argon2id(PW, &SALT, b"pwd").unwrap();
        let b = derive_argon2id(PW, &SALT, b"pwd").unwrap();
        assert_eq!(a, b);
    }

    #[test]
    fn different_salt_different_key() {
        let a = derive_argon2id(PW, &SALT, b"pwd").unwrap();
        let b = derive_argon2id(PW, &[0x22; 16], b"pwd").unwrap();
        assert_ne!(a, b);
    }

    #[test]
    fn rejects_empty_password() {
        assert!(derive_argon2id(b"", &SALT, b"pwd").is_err());
    }

    #[test]
    fn rejects_short_salt() {
        assert!(derive_argon2id(PW, &[0u8; 8], b"pwd").is_err());
    }
}
