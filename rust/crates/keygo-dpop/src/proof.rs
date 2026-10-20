use crate::Htm;
use crate::jwk::Jwk;
use crate::wire::{self, Alg, Claims, Header, Typ};
use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use p256::ecdsa::{Signature, VerifyingKey};
use sha2::{Digest, Sha256};
use std::time::{SystemTime, UNIX_EPOCH};

pub trait DpopSigner {
    type Error: std::error::Error;

    fn verifying_key(&self) -> &VerifyingKey;
    /// ES256 over `msg`. DER-producing backends (Android Keystore) convert with
    /// [`Signature::from_der`]; JWS needs the fixed-size `r || s` form.
    fn sign(&self, msg: &[u8]) -> Result<Signature, Self::Error>;
}

pub struct ProofRequest<'a> {
    pub htm: Htm,
    pub htu: &'a str,
    pub access_token: Option<&'a str>,
    pub nonce: Option<&'a str>,
}

pub struct DpopProver<S> {
    signer: S,
    encoded_header: String,
    jkt: [u8; 32],
}

impl<S: DpopSigner> DpopProver<S> {
    pub fn new(signer: S) -> Self {
        let jwk: Jwk = signer.verifying_key().into();
        let jkt = jwk.thumbprint();
        let encoded_header = wire::encode(&Header {
            typ: Typ::DpopJwt,
            alg: Alg::Es256,
            jwk,
        });

        Self {
            signer,
            encoded_header,
            jkt,
        }
    }

    pub fn jkt(&self) -> &[u8; 32] {
        &self.jkt
    }

    pub fn proof(&self, req: &ProofRequest<'_>) -> Result<String, DpopError<S::Error>> {
        self.proof_at(req, unix_now(), &random_jti()?)
            .map_err(DpopError::Signer)
    }

    pub(crate) fn proof_at(
        &self,
        req: &ProofRequest<'_>,
        iat: u64,
        jti: &str,
    ) -> Result<String, S::Error> {
        let claims = Claims {
            jti: jti.to_owned(),
            htm: req.htm,
            htu: req.htu.to_owned(),
            iat,
            ath: req
                .access_token
                .map(|t| URL_SAFE_NO_PAD.encode(Sha256::digest(t))),
            nonce: req.nonce.map(str::to_owned),
        };

        let input = format!("{}.{}", self.encoded_header, wire::encode(&claims));

        let sig = self.signer.sign(input.as_bytes())?;
        Ok(format!(
            "{input}.{}",
            URL_SAFE_NO_PAD.encode(sig.to_bytes())
        ))
    }
}

#[derive(Debug, thiserror::Error)]
pub enum DpopError<E: std::error::Error> {
    #[error("signer failed")]
    Signer(#[source] E),
    #[error("random number generator failed")]
    Rng(#[from] getrandom::Error),
}

fn random_jti<E: std::error::Error>() -> Result<String, DpopError<E>> {
    let mut buf = [0u8; 16];
    getrandom::fill(&mut buf)?;
    Ok(URL_SAFE_NO_PAD.encode(buf))
}

fn unix_now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("system clock is set before the Unix epoch")
        .as_secs()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::Htm;
    use crate::testing::TestSigner;
    use p256::ecdsa::signature::Verifier;
    use serde_json::Value;

    const HTU: &str = "https://sync.example/api/v1/items";

    fn request<'a>(access_token: Option<&'a str>, nonce: Option<&'a str>) -> ProofRequest<'a> {
        ProofRequest {
            htm: Htm::Post,
            htu: HTU,
            access_token,
            nonce,
        }
    }

    struct Parts {
        header: Value,
        claims: Value,
        signing_input: String,
        signature: Vec<u8>,
    }

    fn split(jwt: &str) -> Parts {
        let parts: Vec<&str> = jwt.split('.').collect();
        assert_eq!(parts.len(), 3, "compact JWS has three segments");

        let json = |s: &str| serde_json::from_slice(&URL_SAFE_NO_PAD.decode(s).unwrap()).unwrap();
        Parts {
            header: json(parts[0]),
            claims: json(parts[1]),
            signing_input: format!("{}.{}", parts[0], parts[1]),
            signature: URL_SAFE_NO_PAD.decode(parts[2]).unwrap(),
        }
    }

    #[test]
    fn header_declares_dpop_type_and_es256() {
        let dpop = DpopProver::new(TestSigner::new());
        let parts = split(
            &dpop
                .proof_at(&request(None, None), 1_700_000_000, "jti")
                .unwrap(),
        );

        assert_eq!(parts.header["typ"], "dpop+jwt");
        assert_eq!(parts.header["alg"], "ES256");
        assert_eq!(
            parts.header["jwk"],
            serde_json::to_value(Jwk::from(&TestSigner::new().verifying)).unwrap(),
        );
        assert_eq!(parts.header.as_object().unwrap().len(), 3);
    }

    #[test]
    fn signature_is_raw_r_s_and_verifies_against_embedded_key() {
        let signer = TestSigner::new();
        let verifying = signer.verifying;
        let dpop = DpopProver::new(signer);
        let parts = split(
            &dpop
                .proof_at(&request(None, None), 1_700_000_000, "jti")
                .unwrap(),
        );

        assert_eq!(parts.signature.len(), 64);
        let sig = Signature::from_slice(&parts.signature).unwrap();
        assert!(
            verifying
                .verify(parts.signing_input.as_bytes(), &sig)
                .is_ok()
        );
    }

    #[test]
    fn claims_carry_request_binding() {
        let dpop = DpopProver::new(TestSigner::new());
        let parts = split(
            &dpop
                .proof_at(&request(None, None), 1_700_000_000, "abc")
                .unwrap(),
        );

        assert_eq!(parts.claims["jti"], "abc");
        assert_eq!(parts.claims["htm"], "POST");
        assert_eq!(parts.claims["htu"], HTU);
        assert_eq!(parts.claims["iat"], 1_700_000_000);
    }

    #[test]
    fn optional_claims_are_omitted_when_absent() {
        let dpop = DpopProver::new(TestSigner::new());
        let parts = split(&dpop.proof_at(&request(None, None), 0, "jti").unwrap());
        let claims = parts.claims.as_object().unwrap();

        assert!(!claims.contains_key("ath"));
        assert!(!claims.contains_key("nonce"));
    }

    #[test]
    fn ath_is_base64url_sha256_of_access_token() {
        let dpop = DpopProver::new(TestSigner::new());
        let parts = split(
            &dpop
                .proof_at(&request(Some("token"), Some("n0nce")), 0, "jti")
                .unwrap(),
        );

        // SHA-256("token"), base64url without padding
        assert_eq!(
            parts.claims["ath"],
            "PEaenWxYddN6Q_NT1PiOYfz4EsZu7jRXRlpAsNpBU-A"
        );
        assert_eq!(parts.claims["nonce"], "n0nce");
    }

    #[test]
    fn jkt_is_thumbprint_of_signer_key() {
        let dpop = DpopProver::new(TestSigner::new());

        assert_eq!(
            dpop.jkt(),
            &Jwk::from(&TestSigner::new().verifying).thumbprint()
        );
    }

    #[test]
    fn proof_uses_fresh_jti_each_call() {
        let dpop = DpopProver::new(TestSigner::new());
        let a = split(&dpop.proof(&request(None, None)).unwrap());
        let b = split(&dpop.proof(&request(None, None)).unwrap());

        assert_ne!(a.claims["jti"], b.claims["jti"]);
    }
}
