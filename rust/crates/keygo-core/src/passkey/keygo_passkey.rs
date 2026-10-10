use coset::{CborSerializable, CoseError, CoseKey};
use passkey::types::Passkey;
use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Serialize, Deserialize)]
pub struct KeyGoPasskey {
    key: Vec<u8>,
    credential_id: Vec<u8>,
    rp_id: String,
    user_handle: Option<Vec<u8>>,
    username: Option<String>,
    user_display_name: Option<String>,
    counter: Option<u32>,
}

#[derive(Serialize, Deserialize)]
pub enum KeyGoPasskeyWire {
    V1(KeyGoPasskey),
}

#[derive(Debug, Error)]
pub enum PasskeyCodecError {
    #[error("cose key error: {0:?}")]
    Cose(CoseError),
    #[error("cbor encode error: {0}")]
    CborEncode(#[from] ciborium::ser::Error<std::io::Error>),
    #[error("cbor decode error: {0}")]
    CborDecode(#[from] ciborium::de::Error<std::io::Error>),
}

impl TryFrom<Passkey> for KeyGoPasskey {
    type Error = PasskeyCodecError;

    fn try_from(value: Passkey) -> Result<Self, Self::Error> {
        let key = value.key.to_vec().map_err(PasskeyCodecError::Cose)?;
        Ok(Self {
            key,
            credential_id: value.credential_id.to_vec(),
            rp_id: value.rp_id,
            user_handle: value.user_handle.map(|b| b.to_vec()),
            username: value.username,
            user_display_name: value.user_display_name,
            counter: value.counter,
        })
    }
}

impl TryFrom<KeyGoPasskey> for Passkey {
    type Error = PasskeyCodecError;

    fn try_from(value: KeyGoPasskey) -> Result<Self, Self::Error> {
        let key = CoseKey::from_slice(&value.key).map_err(PasskeyCodecError::Cose)?;
        Ok(Self {
            key,
            credential_id: value.credential_id.into(),
            rp_id: value.rp_id,
            user_handle: value.user_handle.map(|b| b.into()),
            username: value.username,
            user_display_name: value.user_display_name,
            counter: value.counter,
            extensions: Default::default(),
        })
    }
}

pub fn to_bytes(passkey: Passkey) -> Result<Vec<u8>, PasskeyCodecError> {
    let wire = KeyGoPasskeyWire::V1(KeyGoPasskey::try_from(passkey)?);
    let mut buf = Vec::new();
    ciborium::into_writer(&wire, &mut buf)?;
    Ok(buf)
}

pub fn passkey_from_bytes(bytes: &[u8]) -> Result<Passkey, PasskeyCodecError> {
    let wire: KeyGoPasskeyWire = ciborium::from_reader(bytes)?;
    match wire {
        KeyGoPasskeyWire::V1(kgo_pk) => Passkey::try_from(kgo_pk),
    }
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

    #[test]
    fn roundtrip_preserves_fields() {
        let original = sample_passkey();
        let bytes = to_bytes(original.clone()).expect("encode");
        let decoded = passkey_from_bytes(&bytes).expect("decode");

        assert_eq!(decoded.credential_id, original.credential_id);
        assert_eq!(decoded.rp_id, original.rp_id);
        assert_eq!(decoded.user_handle, original.user_handle);
        assert_eq!(decoded.counter, original.counter);
    }
}
