use crate::domain::Device;
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct Devices {
    pub devices: Vec<Device>,
}
