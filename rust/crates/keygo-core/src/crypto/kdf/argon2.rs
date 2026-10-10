use crate::crypto::{CryptoError, CryptoResult, KEY_LEN};
use argon2::{Algorithm, Argon2, Params, Version};
use zeroize::Zeroizing;

pub(crate) const MIN_SALT_LEN: usize = 16;
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

impl Argon2Params {
    fn build(self) -> CryptoResult<Argon2<'static>> {
        if self.mem_kib > MAX_ARGON2_MEM_KIB {
            return Err(CryptoError::Argon2MemoryTooHigh {
                max: MAX_ARGON2_MEM_KIB,
                got: self.mem_kib,
            });
        }
        let params = Params::new(self.mem_kib, self.iters, self.lanes, Some(KEY_LEN))
            .map_err(CryptoError::Argon2)?;
        Ok(Argon2::new(Algorithm::Argon2id, Version::V0x13, params))
    }
}

/// Derive a key from a password with Argon2id.
///
/// The salt must be at least `MIN_SALT_LEN` bytes and persisted with the credential, so the same
/// key can be derived again later. `domain` is appended to the salt as a label, separating
/// otherwise identical derivations (e.g. the password KEK from a backup key). The costs are explicit
/// so a derivation can be reproduced from costs recorded when it was first made; a header asking
/// for more than `MAX_ARGON2_MEM_KIB` is rejected rather than allowed to exhaust memory.
///
/// The password is not zeroized here: the caller owns that buffer.
pub(crate) fn derive_argon2id(
    password: &[u8],
    salt: &[u8],
    domain: &[u8],
    params: Argon2Params,
) -> CryptoResult<Zeroizing<[u8; KEY_LEN]>> {
    if password.is_empty() {
        return Err(CryptoError::EmptyPassword);
    }
    if salt.len() < MIN_SALT_LEN {
        return Err(CryptoError::SaltTooShort {
            min: MIN_SALT_LEN,
            got: salt.len(),
        });
    }

    let full_salt = [salt, domain].concat();
    let mut out = Zeroizing::new([0u8; KEY_LEN]);
    params
        .build()?
        .hash_password_into(password, &full_salt, out.as_mut_slice())
        .map_err(CryptoError::Argon2)?;
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    const PW: &[u8] = b"correct horse battery staple";
    const SALT: [u8; 16] = [0x11; 16];

    fn derive(password: &[u8], salt: &[u8], domain: &[u8]) -> CryptoResult<[u8; KEY_LEN]> {
        derive_argon2id(password, salt, domain, Argon2Params::default()).map(|k| *k)
    }

    #[test]
    fn deterministic_for_same_inputs() {
        assert_eq!(
            derive(PW, &SALT, b"pwd").unwrap(),
            derive(PW, &SALT, b"pwd").unwrap()
        );
    }

    #[test]
    fn different_salt_different_key() {
        assert_ne!(
            derive(PW, &SALT, b"pwd").unwrap(),
            derive(PW, &[0x22; 16], b"pwd").unwrap()
        );
    }

    #[test]
    fn different_domain_different_key() {
        assert_ne!(
            derive(PW, &SALT, b"v1:kek/pwd").unwrap(),
            derive(PW, &SALT, b"v2:kek/pwd").unwrap()
        );
    }

    #[test]
    fn rejects_empty_password() {
        assert!(matches!(
            derive(b"", &SALT, b"pwd"),
            Err(CryptoError::EmptyPassword)
        ));
    }

    #[test]
    fn rejects_short_salt() {
        assert!(matches!(
            derive(PW, &[0u8; 8], b"pwd"),
            Err(CryptoError::SaltTooShort { min: 16, got: 8 })
        ));
    }

    #[test]
    fn different_params_different_key() {
        let custom = Argon2Params {
            iters: Argon2Params::default().iters + 1,
            ..Argon2Params::default()
        };
        assert_ne!(
            *derive_argon2id(PW, &SALT, b"pwd", custom).unwrap(),
            derive(PW, &SALT, b"pwd").unwrap()
        );
    }

    #[test]
    fn rejects_excessive_memory() {
        let params = Argon2Params {
            mem_kib: MAX_ARGON2_MEM_KIB + 1,
            ..Argon2Params::default()
        };
        assert!(matches!(
            derive_argon2id(PW, &SALT, b"pwd", params),
            Err(CryptoError::Argon2MemoryTooHigh { .. })
        ));
    }

    #[test]
    fn rejects_degenerate_params() {
        let params = Argon2Params {
            iters: 0,
            ..Argon2Params::default()
        };
        assert!(matches!(
            derive_argon2id(PW, &SALT, b"pwd", params),
            Err(CryptoError::Argon2(_))
        ));
    }
}
