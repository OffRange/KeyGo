use coset::CborSerializable;
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

#[derive(Serialize, Deserialize)]
#[serde(tag = "version")]
pub(crate) enum KeyGoPasskeyWire {
    #[serde(rename = "v1")]
    V1(KeyGoPasskey)
}

pub(crate) fn to_bytes(passkey: Passkey) -> Vec<u8> {
    let wire = KeyGoPasskeyWire::V1(KeyGoPasskey::from(passkey));
    serde_cbor::to_vec(&wire).unwrap()
}