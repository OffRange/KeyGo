use crate::domain::SealedBlob;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub struct KeyBundle {
    pub wrapped_ark: SealedBlob,
}
