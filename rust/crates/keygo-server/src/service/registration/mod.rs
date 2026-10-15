mod finish;
mod start;

pub use finish::*;
pub use start::*;

use std::time::Duration;

pub const TTL: Duration = Duration::from_mins(5);
