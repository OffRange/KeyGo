use crate::domain::SealedBlob;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub struct KeyBundle {
    pub wrapped_ark: SealedBlob,
}

#[derive(Serialize)]
pub struct StoredKeyBundle {
    #[serde(flatten)]
    pub bundle: KeyBundle,
    pub updated_at: u64,
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{WRAPPED_ARK, key_bundle_json};
    use serde_json::json;

    #[test]
    fn stored_bundle_serializes_flat() {
        let stored = StoredKeyBundle {
            bundle: KeyBundle {
                wrapped_ark: SealedBlob::from_bytes(&WRAPPED_ARK).unwrap(),
            },
            updated_at: 1_700_000_000,
        };

        assert_eq!(
            serde_json::to_value(&stored).unwrap(),
            json!({
                "wrapped_ark": key_bundle_json()["wrapped_ark"],
                "updated_at": 1_700_000_000,
            }),
        );
    }
}
