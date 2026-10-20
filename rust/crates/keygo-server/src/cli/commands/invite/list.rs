use super::{Connection, format_utc, id_width, with_db};
use crate::service::invite::{self, Invite};
use clap::Args;
use std::iter;

#[derive(Args)]
pub struct ListInviteArgs {
    #[command(flatten)]
    connection: Connection,
}

pub fn run(args: &ListInviteArgs) -> anyhow::Result<()> {
    let invites = with_db(&args.connection, async |db| Ok(invite::list(db).await?))?;
    println!("{}", table(&invites));
    Ok(())
}

fn table(invites: &[Invite]) -> String {
    if invites.is_empty() {
        return "No open invites".into();
    }

    let width = id_width(invites).max("ID".len());
    let header = format!("{:<width$}  {:<20}  EXPIRES", "ID", "CREATED");
    let rows = invites.iter().map(|invite| {
        format!(
            "{:<width$}  {}  {}",
            invite.id.as_str(),
            format_utc(invite.created_at),
            format_utc(invite.expires_at),
        )
    });

    iter::once(header)
        .chain(rows)
        .collect::<Vec<_>>()
        .join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::{InviteHash, InviteId};
    use crate::testing::invite_hash as hash;
    use time::OffsetDateTime;
    use time::macros::datetime;

    fn invite(
        hash: InviteHash,
        open: &[InviteHash],
        created_at: OffsetDateTime,
        expires_at: OffsetDateTime,
    ) -> Invite {
        Invite {
            id: InviteId::new(&hash, open),
            created_at,
            expires_at,
        }
    }

    #[test]
    fn table_pads_ids_to_the_longest() {
        let a = hash(&[0xa3, 0xf9, 0xc1, 0x70]);
        let b = hash(&[0xa3, 0xf9, 0xc1, 0xe0]);
        let c = hash(&[0x5b]);
        let open = [a, b, c];
        let invites = [
            invite(
                a,
                &open,
                datetime!(2026-10-17 08:30 UTC),
                datetime!(2026-10-18 08:30 UTC),
            ),
            invite(
                b,
                &open,
                datetime!(2026-10-17 09:12 UTC),
                datetime!(2026-10-24 09:12 UTC),
            ),
            invite(
                c,
                &open,
                datetime!(2026-10-17 10:00 +02:00),
                datetime!(2026-11-16 10:00 +02:00),
            ),
        ];

        assert_eq!(
            table(&invites),
            [
                "ID       CREATED               EXPIRES",
                "a3f9c17  2026-10-17 08:30 UTC  2026-10-18 08:30 UTC",
                "a3f9c1e  2026-10-17 09:12 UTC  2026-10-24 09:12 UTC",
                "5b000    2026-10-17 08:00 UTC  2026-11-16 08:00 UTC",
            ]
            .join("\n"),
        );
    }

    #[test]
    fn empty_table_says_so() {
        assert_eq!(table(&[]), "No open invites");
    }
}
