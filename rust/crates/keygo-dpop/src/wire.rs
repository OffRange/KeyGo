use crate::jwk::Jwk;
use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use serde::de::{DeserializeOwned, IgnoredAny};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use thiserror::Error;

#[derive(Serialize, Deserialize)]
pub(crate) struct Header {
    pub(crate) typ: Typ,
    pub(crate) alg: Alg,
    pub(crate) jwk: Jwk,

    // RFC 7515 §4.1.11: read only to reject it, as this verifier understands no extension.
    #[serde(default, skip_serializing)]
    pub(crate) crit: Option<IgnoredAny>,
}

#[derive(Serialize, Deserialize)]
pub(crate) struct Claims {
    pub(crate) jti: String,
    pub(crate) htm: Htm,
    pub(crate) htu: String,
    pub(crate) iat: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) ath: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) nonce: Option<String>,
}

pub(crate) fn encode<T: Serialize>(value: &T) -> String {
    URL_SAFE_NO_PAD.encode(serde_json::to_vec(value).expect("wire types always serialize"))
}

#[derive(Debug, Error)]
pub enum DecodeError {
    #[error("invalid base64")]
    Base64(#[from] base64::DecodeError),
    #[error("invalid json")]
    Json(#[from] serde_json::Error),
}

pub(crate) fn ath(access_token: &str) -> String {
    URL_SAFE_NO_PAD.encode(Sha256::digest(access_token))
}

pub(crate) fn decode<T: DeserializeOwned>(value: &str) -> Result<T, DecodeError> {
    let bytes = URL_SAFE_NO_PAD.decode(value)?;
    Ok(serde_json::from_slice(bytes.as_slice())?)
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub(crate) enum Typ {
    #[serde(rename = "dpop+jwt")]
    DpopJwt,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub(crate) enum Alg {
    #[serde(rename = "ES256")]
    Es256,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum Htm {
    Get,
    Post,
    Delete,
}

#[cfg(test)]
mod tests {
    use super::*;
    use p256::ecdsa::SigningKey;

    fn jwk_json() -> String {
        let key = SigningKey::from_slice(&[7u8; 32]).unwrap();
        serde_json::to_string(&Jwk::from(key.verifying_key())).unwrap()
    }

    fn header_json(typ: &str, alg: &str, jwk: &str) -> String {
        format!(r#"{{"typ":"{typ}","alg":"{alg}","jwk":{jwk}}}"#)
    }

    #[test]
    fn header_round_trips() {
        let json = header_json("dpop+jwt", "ES256", &jwk_json());
        let header: Header = serde_json::from_str(&json).unwrap();

        assert_eq!(serde_json::to_string(&header).unwrap(), json);
    }

    #[test]
    fn header_rejects_other_alg() {
        let json = header_json("dpop+jwt", "none", &jwk_json());

        assert!(serde_json::from_str::<Header>(&json).is_err());
    }

    #[test]
    fn header_rejects_other_typ() {
        let json = header_json("JWT", "ES256", &jwk_json());

        assert!(serde_json::from_str::<Header>(&json).is_err());
    }

    #[test]
    fn header_rejects_other_curve() {
        let jwk = jwk_json().replace("P-256", "P-384");
        let json = header_json("dpop+jwt", "ES256", &jwk);

        assert!(serde_json::from_str::<Header>(&json).is_err());
    }

    #[test]
    fn header_reads_crit() {
        let json = format!(
            r#"{{"typ":"dpop+jwt","alg":"ES256","jwk":{},"crit":["exp"]}}"#,
            jwk_json()
        );
        let header: Header = serde_json::from_str(&json).unwrap();

        assert!(header.crit.is_some());
    }

    #[test]
    fn claims_accept_escaped_strings() {
        let json = r#"{"jti":"j","htm":"GET","htu":"https:\/\/sync.example\/x","iat":1}"#;
        let claims: Claims = serde_json::from_str(json).unwrap();

        assert_eq!(claims.htu, "https://sync.example/x");
        assert!(claims.ath.is_none());
        assert!(claims.nonce.is_none());
    }
}
