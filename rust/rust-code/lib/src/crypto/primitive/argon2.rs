use crate::crypto::error::{CryptoError, CryptoResult};
use argon2::{Algorithm, Argon2, Params, Version};

pub const MIN_SALT_LEN: usize = 16;
pub const DERIVED_KEY_LEN: usize = 32;
pub(crate) const MAX_ARGON2_MEM_KIB: u32 = 256 * 1024;

#[derive(Clone, Copy)]
pub(crate) struct Argon2Params {
    /// Memory cost in KiB
    pub mem_kib: u32,
    /// Iterations / time cost
    pub iters: u32,
    /// Degree of parallelism
    pub lanes: u32,
}

impl Default for Argon2Params {
    fn default() -> Self {
        Self {
            mem_kib: 64 * 1024,
            iters: 3,
            lanes: 4,
        }
    }
}

fn build_argon<'a>(params: Argon2Params) -> CryptoResult<Argon2<'a>> {
    if params.mem_kib > MAX_ARGON2_MEM_KIB {
        return Err(CryptoError::KdfError(format!(
            "argon2 mem_kib too large: {} > {}",
            params.mem_kib, MAX_ARGON2_MEM_KIB,
        )));
    }
    let params = Params::new(
        params.mem_kib,
        params.iters,
        params.lanes,
        Some(DERIVED_KEY_LEN),
    )
    .map_err(|e| CryptoError::KdfError(e.to_string()))?;
    Ok(Argon2::new(Algorithm::Argon2id, Version::V0x13, params))
}

/// Derive `DERIVED_KEY_LEN` bytes from a password using Argon2id with the
/// [`Argon2Params::default`] cost profile. See [`derive_argon2id_with_params`].
pub(crate) fn derive_argon2id(
    password: &[u8],
    salt: &[u8],
    domain: &[u8],
) -> CryptoResult<[u8; DERIVED_KEY_LEN]> {
    derive_argon2id_with_params(password, salt, domain, Argon2Params::default())
}

/// Derive `DERIVED_KEY_LEN` bytes from a password using Argon2id with explicit
/// cost [`Argon2Params`], so a derivation can be reproduced from costs recorded
/// at seal time. Invalid or over-limit params yield [`CryptoError::KdfError`]
/// rather than panicking.
///
/// The salt must be caller-provided, at least `MIN_SALT_LEN` bytes, and persisted with the
/// credential so the same KEK can be re-derived on later logins. `domain` is mixed into the salt
/// as a label to separate otherwise-identical derivations (e.g. password-KEK vs recovery-KEK).
///
/// The password is not zeroized by this function — the caller owns the password buffer and must
/// wrap it in `Zeroizing` / a secret type at the FFI boundary.
pub(crate) fn derive_argon2id_with_params(
    password: &[u8],
    salt: &[u8],
    domain: &[u8],
    params: Argon2Params,
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

    let argon2 = build_argon(params)?;

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

    #[test]
    fn different_params_different_key() {
        let a = derive_argon2id_with_params(PW, &SALT, b"pwd", Argon2Params::default()).unwrap();
        let b = derive_argon2id_with_params(
            PW,
            &SALT,
            b"pwd",
            Argon2Params {
                iters: Argon2Params::default().iters + 1,
                ..Argon2Params::default()
            },
        )
        .unwrap();
        assert_ne!(a, b);
    }

    #[test]
    fn default_params_match_plain_derive() {
        let plain = derive_argon2id(PW, &SALT, b"pwd").unwrap();
        let explicit =
            derive_argon2id_with_params(PW, &SALT, b"pwd", Argon2Params::default()).unwrap();
        assert_eq!(plain, explicit);
    }

    #[test]
    fn rejects_excessive_memory() {
        let err = derive_argon2id_with_params(
            PW,
            &SALT,
            b"pwd",
            Argon2Params {
                mem_kib: MAX_ARGON2_MEM_KIB + 1,
                ..Argon2Params::default()
            },
        )
        .unwrap_err();
        assert!(matches!(err, CryptoError::KdfError(_)));
    }

    #[test]
    fn rejects_degenerate_params() {
        let err = derive_argon2id_with_params(
            PW,
            &SALT,
            b"pwd",
            Argon2Params {
                iters: 0,
                ..Argon2Params::default()
            },
        )
        .unwrap_err();
        assert!(matches!(err, CryptoError::KdfError(_)));
    }
}
