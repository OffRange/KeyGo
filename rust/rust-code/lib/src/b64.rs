use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use serde::{Deserialize, Deserializer, Serializer};

pub(crate) fn encode(bytes: impl AsRef<[u8]>) -> String {
    STANDARD.encode(bytes)
}

pub(crate) fn decode(encoded: &str) -> Result<Vec<u8>, base64::DecodeError> {
    STANDARD.decode(encoded)
}

/// serde adapter for `#[serde(with = "crate::backup::b64")]` on `Vec<u8>` fields.
pub(crate) fn serialize<S: Serializer>(bytes: &[u8], serializer: S) -> Result<S::Ok, S::Error> {
    serializer.serialize_str(&encode(bytes))
}

pub(crate) fn deserialize<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Vec<u8>, D::Error> {
    let s = String::deserialize(deserializer)?;
    decode(&s).map_err(serde::de::Error::custom)
}

#[cfg(test)]
mod tests {
    use serde::{Deserialize, Serialize};

    #[derive(Serialize, Deserialize, PartialEq, Debug)]
    struct Holder {
        #[serde(with = "crate::b64")]
        data: Vec<u8>,
    }

    #[test]
    fn round_trip() {
        let h = Holder {
            data: vec![0, 1, 2, 250, 255],
        };
        let json = serde_json::to_string(&h).unwrap();
        assert_eq!(json, r#"{"data":"AAEC+v8="}"#);
        let back: Holder = serde_json::from_str(&json).unwrap();
        assert_eq!(back, h);
    }
}
