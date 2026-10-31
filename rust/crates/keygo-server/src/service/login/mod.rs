mod finish;
mod start;

pub use finish::*;
pub use start::*;

use keygo_opaque::OpaqueError;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("login expired or unknown")]
    Expired,
    #[error(transparent)]
    Opaque(#[from] OpaqueError),
    #[error("database error")]
    Db(#[from] sqlx::Error),
}
