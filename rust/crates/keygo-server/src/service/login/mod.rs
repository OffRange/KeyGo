mod finish;
mod start;

pub use finish::*;
pub use start::*;

use keygo_opaque::OpaqueError;
use std::time::Duration;

pub const TTL: Duration = Duration::from_mins(1);

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("login expired or unknown")]
    Expired,
    #[error(transparent)]
    Opaque(#[from] OpaqueError),
    #[error("database error")]
    Db(#[from] sqlx::Error),
}
