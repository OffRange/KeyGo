use crate::error::{OpaqueError, OpaqueResult};
use crate::keys::SessionKey;
use hkdf::Hkdf;
use hkdf::hmac::{Hmac, Mac};
use sha2::Sha256;
use zeroize::Zeroizing;

const DPOP_BINDING_LABEL: &[u8] = b"v1:bind/dpop";

/// `HMAC-SHA256(HKDF-SHA256(session_key, salt = "", info = "v1:bind/dpop"), jkt)`.
pub struct DpopBinding([u8; 32]);

impl DpopBinding {
    pub fn to_bytes(&self) -> [u8; 32] {
        self.0
    }

    pub fn from_bytes(bytes: &[u8]) -> OpaqueResult<Self> {
        Ok(Self(bytes.try_into().map_err(|_| OpaqueError::Malformed)?))
    }
}

wire_message!(DpopBinding);

impl SessionKey {
    pub fn dpop_binding(&self, jkt: &[u8; 32]) -> DpopBinding {
        DpopBinding(self.binding_mac(jkt).finalize().into_bytes().into())
    }

    pub fn verify_dpop_binding(&self, jkt: &[u8; 32], binding: &DpopBinding) -> OpaqueResult<()> {
        self.binding_mac(jkt)
            .verify_slice(&binding.0)
            .map_err(|_| OpaqueError::InvalidLogin)
    }

    fn binding_mac(&self, jkt: &[u8; 32]) -> Hmac<Sha256> {
        let mut key = Zeroizing::new([0u8; 32]);
        Hkdf::<Sha256>::new(None, self.as_bytes())
            .expand(DPOP_BINDING_LABEL, key.as_mut())
            .expect("32 bytes is a valid HKDF-SHA256 output length");

        let mut mac =
            Hmac::<Sha256>::new_from_slice(key.as_ref()).expect("HMAC accepts any key length");
        mac.update(jkt);
        mac
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use opaque_ke::generic_array::GenericArray;

    const JKT: [u8; 32] = [0x40; 32];

    fn session_key(bytes: &[u8; 64]) -> SessionKey {
        SessionKey::take(GenericArray::clone_from_slice(bytes))
    }

    fn sequence<const N: usize>(start: u8) -> [u8; N] {
        core::array::from_fn(|i| start + i as u8)
    }

    /// Computed independently (Python `hmac`/`hashlib`), so a change to the derivation, the label
    /// or the salt fails here instead of between a released client and server.
    #[test]
    fn binding_is_pinned() {
        let key = session_key(&sequence(0x00));
        let jkt = sequence(0x40);

        assert_eq!(
            key.dpop_binding(&jkt).to_bytes(),
            [
                0x1e, 0x38, 0xaf, 0x83, 0x21, 0x81, 0x20, 0xa2, 0x40, 0xb4, 0x85, 0x29, 0x07, 0x13,
                0xd9, 0x9b, 0x8e, 0x4e, 0xeb, 0xaa, 0x1a, 0x63, 0x91, 0x8c, 0xf5, 0xf9, 0x27, 0xff,
                0x1b, 0x39, 0x0d, 0x23,
            ],
        );
    }

    #[test]
    fn verifies_own_binding() {
        let key = session_key(&[1; 64]);

        assert_eq!(
            key.verify_dpop_binding(&JKT, &key.dpop_binding(&JKT)),
            Ok(())
        );
    }

    #[test]
    fn rejects_binding_for_another_key() {
        let key = session_key(&[1; 64]);

        assert_eq!(
            key.verify_dpop_binding(&[0x41; 32], &key.dpop_binding(&JKT)),
            Err(OpaqueError::InvalidLogin),
        );
    }

    #[test]
    fn rejects_binding_from_another_session() {
        let binding = session_key(&[2; 64]).dpop_binding(&JKT);

        assert_eq!(
            session_key(&[1; 64]).verify_dpop_binding(&JKT, &binding),
            Err(OpaqueError::InvalidLogin),
        );
    }

    #[test]
    fn survives_base64() {
        let binding = session_key(&[1; 64]).dpop_binding(&JKT);
        let restored = DpopBinding::from_base64(&binding.to_base64()).unwrap();

        assert_eq!(restored.to_bytes(), binding.to_bytes());
    }

    #[test]
    fn wrong_length_is_malformed() {
        assert!(matches!(
            DpopBinding::from_bytes(&[0; 31]),
            Err(OpaqueError::Malformed)
        ));
        assert!(matches!(
            DpopBinding::from_base64("not base64!"),
            Err(OpaqueError::Malformed)
        ));
    }
}
