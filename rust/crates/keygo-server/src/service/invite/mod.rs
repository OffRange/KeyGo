mod create;

pub use create::*;

use crate::db::invites::OpenInvite;
use crate::domain::{InviteHash, InviteId};
use time::OffsetDateTime;

#[derive(Debug)]
pub struct Invite {
    pub id: InviteId,
    pub created_at: OffsetDateTime,
    pub expires_at: OffsetDateTime,
}

impl Invite {
    fn new(invite: OpenInvite, open: &[InviteHash]) -> Self {
        Self {
            id: InviteId::new(&invite.code_hash, open),
            created_at: invite.created_at,
            expires_at: invite.expires_at,
        }
    }
}
