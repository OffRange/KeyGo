use crate::domain::SealedBlob;
use keygo_core::{AccountRootKey, Sealed};
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub struct KeyBundle {
    pub wrapped_ark: SealedBlob,
}

impl KeyBundle {
    /// The protocol fixes this field as the ARK wrapped under the account's OPAQUE KEK.
    pub fn into_wrapped_ark(self) -> Sealed<AccountRootKey> {
        self.wrapped_ark.assume()
    }
}
