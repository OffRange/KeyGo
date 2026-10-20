use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use p256::ecdsa::VerifyingKey;
use p256::{FieldBytes, Sec1Point};
use serde::de::IgnoredAny;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use thiserror::Error;

/// Public P-256 key as a JWK.
///
/// Field order is load-bearing: RFC 7638 hashes the required members in lexicographic order.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Jwk {
    crv: Crv,
    kty: Kty,
    x: String,
    y: String,

    // Only read so a received private key can be rejected; never serialized, so it cannot leak
    // into a header or change the thumbprint.
    #[serde(default, skip_serializing)]
    d: Option<IgnoredAny>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
enum Crv {
    #[serde(rename = "P-256")]
    P256,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
enum Kty {
    #[serde(rename = "EC")]
    Ec,
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
            crv: Crv::P256,
            kty: Kty::Ec,
            x,
            y,
            d: None,
        }
    }
}

#[derive(Debug, Error)]
pub enum JwkError {
    #[error("jwk contains a private key")]
    PrivateKey,
    #[error("invalid base64url in coordinate")]
    Base64(#[from] base64::DecodeError),
    #[error("coordinate must be 32 bytes")]
    CoordinateLength,
    #[error("point is not on the P-256 curve")]
    InvalidPoint,
}

impl Jwk {
    pub(crate) fn verifying_key(&self) -> Result<VerifyingKey, JwkError> {
        if self.d.is_some() {
            return Err(JwkError::PrivateKey);
        }

        let x = URL_SAFE_NO_PAD.decode(&self.x)?;
        let y = URL_SAFE_NO_PAD.decode(&self.y)?;
        let x: &FieldBytes = x
            .as_slice()
            .try_into()
            .map_err(|_| JwkError::CoordinateLength)?;
        let y: &FieldBytes = y
            .as_slice()
            .try_into()
            .map_err(|_| JwkError::CoordinateLength)?;

        let point = Sec1Point::from_affine_coordinates(x, y, false);
        VerifyingKey::from_sec1_point(&point).map_err(|_| JwkError::InvalidPoint)
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
