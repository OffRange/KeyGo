use bincode::error::{DecodeError, EncodeError};
use coset::{CborSerializable, CoseKey};
use passkey::types::Passkey;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub(crate) struct KeyGoPasskey {
    key: Vec<u8>,
    credential_id: Vec<u8>,
    rp_id: String,
    user_handle: Option<Vec<u8>>,
    counter: Option<u32>,
}

impl From<Passkey> for KeyGoPasskey {
    fn from(value: Passkey) -> Self {
        Self {
            key: value.key.to_vec().unwrap(),
            credential_id: value.credential_id.to_vec(),
            rp_id: value.rp_id,
            user_handle: value.user_handle.map(|b| b.to_vec()),
            counter: value.counter,
        }
    }
}

impl From<KeyGoPasskey> for Passkey {
    fn from(value: KeyGoPasskey) -> Self {
        Self {
            key: CoseKey::from_slice(&value.key).unwrap(),
            credential_id: value.credential_id.into(),
            rp_id: value.rp_id,
            user_handle: value.user_handle.map(|b| b.into()),
            counter: value.counter,
            extensions: Default::default(),
        }
    }
}

#[derive(Serialize, Deserialize)]
pub(crate) enum KeyGoPasskeyWire {
    V1(KeyGoPasskey)
}

pub(crate) fn to_bytes(passkey: Passkey) -> Result<Vec<u8>, EncodeError> {
    let wire = KeyGoPasskeyWire::V1(KeyGoPasskey::from(passkey));
    bincode::serde::encode_to_vec(&wire, bincode::config::standard())
}

pub(crate) fn passkey_from_bytes(passkey: &[u8]) -> Result<Passkey, DecodeError> {
    let (wire, _) = bincode::serde::decode_from_slice::<KeyGoPasskeyWire, _>(passkey, bincode::config::standard())?;

    match wire {
        KeyGoPasskeyWire::V1(kgo_pk) => Ok(Passkey::from(kgo_pk))
    }
}