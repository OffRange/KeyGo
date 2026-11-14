use keygo_core::{AccountRootKey, Sealed};
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub struct KeyBundle {
    pub wrapped_ark: Sealed<AccountRootKey>,
}
