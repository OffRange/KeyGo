use super::{format_utc, id_width, with_db};
use crate::config::Config;
use crate::domain::InviteId;
use crate::service::invite::{self, Invite, RevokeError};
use anyhow::anyhow;
use clap::Args;

#[derive(Args)]
pub struct RevokeInviteArgs {
    /// Any prefix of the invite's hash, 5 to 64 hex characters, such as the id `invite list` prints.
    id: InviteId,
}

pub fn run(config: &Config, args: &RevokeInviteArgs) -> anyhow::Result<()> {
    with_db(config, async |db| {
        invite::revoke(db, &args.id).await.map_err(|err| match err {
            RevokeError::Ambiguous { ref matches, .. } => {
                anyhow!("{err}:\n{}", candidates(matches))
            }
            err => err.into(),
        })
    })?;

    println!("Revoked {}", args.id);
    Ok(())
}

fn candidates(invites: &[Invite]) -> String {
    let width = id_width(invites);
    invites
        .iter()
        .map(|invite| {
            format!(
                "  {:<width$}  created {}",
                invite.id.as_str(),
                format_utc(invite.created_at),
            )
        })
        .collect::<Vec<_>>()
        .join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::invite_hash as hash;
    use time::macros::datetime;

    #[test]
    fn candidates_line_up_their_dates() {
        let a = hash(&[0xa3, 0xf9, 0xc1, 0x70]);
        let b = hash(&[0xa3, 0xf9, 0xc1, 0x7e]);
        let open = [a, b];
        let invite = |hash, created_at| Invite {
            id: InviteId::new(&hash, &open),
            created_at,
            expires_at: datetime!(2026-10-30 00:00 UTC),
        };

        assert_eq!(
            candidates(&[
                invite(a, datetime!(2026-10-16 14:03 UTC)),
                invite(b, datetime!(2026-10-17 08:30 UTC)),
            ]),
            [
                "  a3f9c170  created 2026-10-16 14:03 UTC",
                "  a3f9c17e  created 2026-10-17 08:30 UTC",
            ]
            .join("\n"),
        );
    }
}
