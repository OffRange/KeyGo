//! The bytes a passkey is stored as. Shipped passkeys are in this format: the `V1` tag and the
//! field names of [`StoredPasskey`] must not change.

use coset::{CborSerializable, CoseError, CoseKey};
use passkey::types::Passkey;
use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Serialize, Deserialize)]
struct StoredPasskey {
    key: Vec<u8>,
    credential_id: Vec<u8>,
    rp_id: String,
    user_handle: Option<Vec<u8>>,
    username: Option<String>,
    user_display_name: Option<String>,
    counter: Option<u32>,
}

#[derive(Serialize, Deserialize)]
enum Versioned {
    V1(StoredPasskey),
}

#[derive(Debug, Error)]
pub enum CodecError {
    #[error("cose key error: {0:?}")]
    Cose(CoseError),
    #[error("cbor encode error: {0}")]
    CborEncode(#[from] ciborium::ser::Error<std::io::Error>),
    #[error("cbor decode error: {0}")]
    CborDecode(#[from] ciborium::de::Error<std::io::Error>),
}

pub(crate) fn encode(passkey: Passkey) -> Result<Vec<u8>, CodecError> {
    let stored = StoredPasskey {
        key: passkey.key.to_vec().map_err(CodecError::Cose)?,
        credential_id: passkey.credential_id.to_vec(),
        rp_id: passkey.rp_id,
        user_handle: passkey.user_handle.map(|b| b.to_vec()),
        username: passkey.username,
        user_display_name: passkey.user_display_name,
        counter: passkey.counter,
    };
    let mut buf = Vec::new();
    ciborium::into_writer(&Versioned::V1(stored), &mut buf)?;
    Ok(buf)
}

pub(crate) fn decode(bytes: &[u8]) -> Result<Passkey, CodecError> {
    let Versioned::V1(stored) = ciborium::from_reader(bytes)?;
    Ok(Passkey {
        key: CoseKey::from_slice(&stored.key).map_err(CodecError::Cose)?,
        credential_id: stored.credential_id.into(),
        rp_id: stored.rp_id,
        user_handle: stored.user_handle.map(Into::into),
        username: stored.username,
        user_display_name: stored.user_display_name,
        counter: stored.counter,
        extensions: Default::default(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use coset::CoseKeyBuilder;
    use coset::iana;

    fn sample_passkey() -> Passkey {
        let key = CoseKeyBuilder::new_okp_key()
            .algorithm(iana::Algorithm::EdDSA)
            .build();
        Passkey {
            key,
            credential_id: vec![1, 2, 3, 4].into(),
            rp_id: "example.com".to_string(),
            user_handle: Some(vec![9, 9, 9].into()),
            username: Some("alice".to_string()),
            user_display_name: Some("Alice".to_string()),
            counter: Some(42),
            extensions: Default::default(),
        }
    }

    /// `sample_passkey` as encoded when the format shipped. Every stored passkey is in this
    /// shape, so the decoder must keep reading it: the `V1` tag and the field names are the format.
    const GOLDEN_V1: &str = "oWJWMadja2V5hRiiAQEDGCdtY3JlZGVudGlhbF9pZIQBAgMEZXJwX2lka2V4YW1wbGUuY29ta3VzZXJfaGFuZGxlgwkJCWh1c2VybmFtZWVhbGljZXF1c2VyX2Rpc3BsYXlfbmFtZWVBbGljZWdjb3VudGVyGCo=";

    #[test]
    fn golden_v1_still_decodes() {
        let original = sample_passkey();
        let decoded = decode(&crate::b64::decode(GOLDEN_V1).unwrap()).unwrap();

        assert_eq!(decoded.key, original.key);
        assert_eq!(decoded.credential_id, original.credential_id);
        assert_eq!(decoded.rp_id, original.rp_id);
        assert_eq!(decoded.user_handle, original.user_handle);
        assert_eq!(decoded.username, original.username);
        assert_eq!(decoded.user_display_name, original.user_display_name);
        assert_eq!(decoded.counter, original.counter);
    }

    #[test]
    fn encoding_is_stable() {
        assert_eq!(
            crate::b64::encode(encode(sample_passkey()).unwrap()),
            GOLDEN_V1
        );
    }

    #[test]
    fn roundtrip_preserves_fields() {
        let original = sample_passkey();
        let bytes = encode(original.clone()).expect("encode");
        let decoded = decode(&bytes).expect("decode");

        assert_eq!(decoded.credential_id, original.credential_id);
        assert_eq!(decoded.rp_id, original.rp_id);
        assert_eq!(decoded.user_handle, original.user_handle);
        assert_eq!(decoded.counter, original.counter);
    }
}
