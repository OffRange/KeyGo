mod finish;
mod start;

pub use finish::*;
pub use start::*;

use crate::domain::InvitePepper;
use std::sync::Arc;
use std::time::Duration;

pub const TTL: Duration = Duration::from_mins(5);

#[derive(Clone)]
pub enum Signup {
    Open,
    InviteOnly(Arc<InvitePepper>),
}
