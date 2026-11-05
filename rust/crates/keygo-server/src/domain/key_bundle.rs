use crate::domain::SealedBlob;
use serde::Deserialize;

#[derive(Deserialize)]
pub struct KeyBundle {
    pub wrapped_ark: SealedBlob,
}
