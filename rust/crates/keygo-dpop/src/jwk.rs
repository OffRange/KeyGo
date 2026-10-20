use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use p256::ecdsa::VerifyingKey;
use serde::Serialize;
use sha2::{Digest, Sha256};

/// Public P-256 key as a JWK.
///
/// Field order is load-bearing: RFC 7638 hashes the required members in lexicographic order.
#[derive(Debug, Serialize)]
pub struct Jwk {
    crv: &'static str,
    kty: &'static str,
    x: String,
    y: String,
}

impl Jwk {
    pub fn thumbprint(&self) -> [u8; 32] {
        let canonical = serde_json::to_vec(self).expect("Jwk always serializes");
        Sha256::digest(canonical).into()
    }
}

impl From<&VerifyingKey> for Jwk {
    fn from(v: &VerifyingKey) -> Self {
        let point = v.to_sec1_point(false);

        let x = URL_SAFE_NO_PAD.encode(point.x().expect("x coordinate"));
        let y = URL_SAFE_NO_PAD.encode(point.y().expect("y coordinate"));

        Self {
            crv: "P-256",
            kty: "EC",
            x,
            y,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use p256::ecdsa::SigningKey;

    fn jwk() -> Jwk {
        let key = SigningKey::from_slice(&[7u8; 32]).unwrap();
        Jwk::from(key.verifying_key())
    }

    #[test]
    fn serializes_members_in_rfc7638_order() {
        let jwk = jwk();
        let expected = format!(
            r#"{{"crv":"P-256","kty":"EC","x":"{}","y":"{}"}}"#,
            jwk.x, jwk.y,
        );

        assert_eq!(serde_json::to_string(&jwk).unwrap(), expected);
    }

    #[test]
    fn coordinates_are_32_byte_base64url() {
        let jwk = jwk();

        assert_eq!(URL_SAFE_NO_PAD.decode(&jwk.x).unwrap().len(), 32);
        assert_eq!(URL_SAFE_NO_PAD.decode(&jwk.y).unwrap().len(), 32);
    }

    #[test]
    fn thumbprint_is_sha256_of_canonical_json() {
        let jwk = jwk();
        let canonical = serde_json::to_vec(&jwk).unwrap();

        assert_eq!(
            jwk.thumbprint(),
            <[u8; 32]>::from(Sha256::digest(canonical))
        );
    }
}
