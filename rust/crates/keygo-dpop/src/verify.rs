use crate::wire::{Claims, Header};
use crate::{Htm, jwk, wire};
use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use p256::ecdsa::Signature;
use p256::ecdsa::signature::Verifier;
use sha2::{Digest, Sha256};
use thiserror::Error;

pub struct Expected<'a> {
    pub htm: Htm,
    pub htu: &'a str,
    pub access_token: Option<&'a str>,
}

pub struct VerifiedProof {
    pub jkt: [u8; 32],
    pub jti: String,
    pub iat: u64,
}

#[derive(Debug, Error)]
pub enum VerifyError {
    #[error("proof malformed")]
    Malformed,
    #[error(transparent)]
    DecodeError(#[from] wire::DecodeError),
    #[error(transparent)]
    JwkError(#[from] jwk::JwkError),
    #[error("Signature verification failed")]
    Signature,
    #[error("invalid request")]
    Request,
    #[error("invalid access token")]
    AccessTokenHash,
}

type VerifyResult<T> = Result<T, VerifyError>;

pub fn verify(proof: &str, expected: Expected) -> VerifyResult<VerifiedProof> {
    let (input, sign) = proof.rsplit_once('.').ok_or(VerifyError::Malformed)?;
    let (header, claims) = input.split_once('.').ok_or(VerifyError::Malformed)?;

    let header = wire::decode::<Header>(header)?;
    let key = header.jwk.verifying_key()?;
    let sign = URL_SAFE_NO_PAD
        .decode(sign)
        .map_err(|_| VerifyError::Signature)?;
    let sign = Signature::from_slice(&sign).map_err(|_| VerifyError::Signature)?;
    key.verify(input.as_bytes(), &sign)
        .map_err(|_| VerifyError::Signature)?;

    let Claims {
        jti,
        htm,
        htu,
        iat,
        ath,
        nonce: _,
    } = wire::decode(claims)?;

    if htm != expected.htm || htu != expected.htu {
        return Err(VerifyError::Request);
    }

    let expected_ath = expected
        .access_token
        .map(|t| URL_SAFE_NO_PAD.encode(Sha256::digest(t)));
    if ath != expected_ath {
        return Err(VerifyError::AccessTokenHash);
    }

    Ok(VerifiedProof {
        jkt: header.jwk.thumbprint(),
        jti,
        iat,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::TestSigner;
    use crate::{DpopProver, ProofRequest};

    const HTU: &str = "https://sync.example/api/v1/items";
    const NOW: u64 = 1_700_000_000;

    fn proof(iat: u64, access_token: Option<&str>) -> String {
        let req = ProofRequest {
            htm: Htm::Post,
            htu: HTU,
            access_token,
            nonce: None,
        };
        DpopProver::new(TestSigner::new())
            .proof_at(&req, iat, "jti")
            .unwrap()
    }

    fn expected(access_token: Option<&str>) -> Expected<'_> {
        Expected {
            htm: Htm::Post,
            htu: HTU,
            access_token,
        }
    }

    /// Swaps one segment of a compact JWS, leaving the others untouched.
    fn replace_segment(proof: &str, index: usize, segment: &str) -> String {
        let mut parts: Vec<&str> = proof.split('.').collect();
        parts[index] = segment;
        parts.join(".")
    }

    #[test]
    fn accepts_own_proof() {
        let verified = verify(&proof(NOW, None), expected(None)).unwrap();

        assert_eq!(&verified.jkt, DpopProver::new(TestSigner::new()).jkt());
        assert_eq!(verified.jti, "jti");
        assert_eq!(verified.iat, NOW);
    }

    #[test]
    fn accepts_matching_access_token() {
        assert!(verify(&proof(NOW, Some("token")), expected(Some("token"))).is_ok());
    }

    #[test]
    fn rejects_wrong_access_token() {
        let result = verify(&proof(NOW, Some("token")), expected(Some("other")));

        assert!(matches!(result, Err(VerifyError::AccessTokenHash)));
    }

    #[test]
    fn rejects_missing_ath_when_token_present() {
        let result = verify(&proof(NOW, None), expected(Some("token")));

        assert!(matches!(result, Err(VerifyError::AccessTokenHash)));
    }

    #[test]
    fn rejects_unexpected_ath() {
        let result = verify(&proof(NOW, Some("token")), expected(None));

        assert!(matches!(result, Err(VerifyError::AccessTokenHash)));
    }

    #[test]
    fn rejects_other_method_or_uri() {
        let mut wrong_htm = expected(None);
        wrong_htm.htm = Htm::Get;
        let mut wrong_htu = expected(None);
        wrong_htu.htu = "https://sync.example/api/v1/other";

        assert!(matches!(
            verify(&proof(NOW, None), wrong_htm),
            Err(VerifyError::Request)
        ));
        assert!(matches!(
            verify(&proof(NOW, None), wrong_htu),
            Err(VerifyError::Request)
        ));
    }

    #[test]
    fn rejects_tampered_claims() {
        let other = proof(NOW + 1, None);
        let claims = other.split('.').nth(1).unwrap();
        let tampered = replace_segment(&proof(NOW, None), 1, claims);

        assert!(matches!(
            verify(&tampered, expected(None)),
            Err(VerifyError::Signature)
        ));
    }

    #[test]
    fn rejects_proof_signed_by_another_key() {
        let foreign = DpopProver::new(TestSigner::from_seed(9))
            .proof_at(
                &ProofRequest {
                    htm: Htm::Post,
                    htu: HTU,
                    access_token: None,
                    nonce: None,
                },
                NOW,
                "jti",
            )
            .unwrap();
        let signature = foreign.rsplit('.').next().unwrap();
        let forged = replace_segment(&proof(NOW, None), 2, signature);

        assert!(matches!(
            verify(&forged, expected(None)),
            Err(VerifyError::Signature)
        ));
    }

    #[test]
    fn rejects_jwk_with_private_key() {
        let header = proof(NOW, None).split('.').next().unwrap().to_owned();
        let mut json: serde_json::Value =
            serde_json::from_slice(&URL_SAFE_NO_PAD.decode(header).unwrap()).unwrap();
        json["jwk"]["d"] = "c2VjcmV0".into();
        let header = URL_SAFE_NO_PAD.encode(serde_json::to_vec(&json).unwrap());
        let leaked = replace_segment(&proof(NOW, None), 0, &header);

        assert!(matches!(
            verify(&leaked, expected(None)),
            Err(VerifyError::JwkError(jwk::JwkError::PrivateKey)),
        ));
    }

    #[test]
    fn rejects_non_base64_jwk_coordinate_without_panicking() {
        let header = proof(NOW, None).split('.').next().unwrap().to_owned();
        let mut json: serde_json::Value =
            serde_json::from_slice(&URL_SAFE_NO_PAD.decode(header).unwrap()).unwrap();
        json["jwk"]["x"] = "not base64!".into();
        let header = URL_SAFE_NO_PAD.encode(serde_json::to_vec(&json).unwrap());
        let broken = replace_segment(&proof(NOW, None), 0, &header);

        assert!(matches!(
            verify(&broken, expected(None)),
            Err(VerifyError::JwkError(jwk::JwkError::Base64(_))),
        ));
    }

    #[test]
    fn rejects_missing_segments() {
        assert!(matches!(
            verify("abc", expected(None)),
            Err(VerifyError::Malformed)
        ));
        assert!(matches!(
            verify("abc.def", expected(None)),
            Err(VerifyError::Malformed)
        ));
    }
}
