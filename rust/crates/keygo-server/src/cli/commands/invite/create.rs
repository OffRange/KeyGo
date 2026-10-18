use super::{Connection, format_utc, with_db};
use crate::service::invite::{self, Created};
use anyhow::ensure;
use clap::Args;
use std::time::Duration;

const MIN_TTL: Duration = Duration::from_hours(24);
const MAX_TTL: Duration = Duration::from_hours(30 * 24);

#[derive(Args)]
pub struct CreateInviteArgs {
    /// How long the invite stays redeemable, from 1 to 30 days, e.g. "72h" or "2weeks".
    #[arg(short, long, default_value = "1d", value_parser = parse_ttl)]
    ttl: Duration,
    #[command(flatten)]
    connection: Connection,
}

fn parse_ttl(s: &str) -> anyhow::Result<Duration> {
    let ttl = humantime::parse_duration(s)?;
    ensure!(
        ttl >= MIN_TTL,
        "ttl must be at least {}",
        humantime::format_duration(MIN_TTL)
    );
    ensure!(
        ttl <= MAX_TTL,
        "ttl must be at most {}",
        humantime::format_duration(MAX_TTL)
    );
    Ok(ttl)
}

pub fn run(args: &CreateInviteArgs) -> anyhow::Result<()> {
    let pepper = args.connection.secrets.invite_pepper()?;

    let created = with_db(&args.connection, async |db| {
        Ok(invite::create(db, &pepper, args.ttl).await?)
    })?;

    println!("{}", summary(&created));
    Ok(())
}

fn summary(created: &Created) -> String {
    let Created { code, invite } = created;
    format!(
        "Invite  {code}   expires {}   id {}",
        format_utc(invite.expires_at),
        invite.id,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::InviteId;
    use crate::service::invite::Invite;
    use time::macros::datetime;

    #[test]
    fn ttl_accepts_its_bounds() {
        assert_eq!(parse_ttl("1d").unwrap(), MIN_TTL);
        assert_eq!(parse_ttl("30d").unwrap(), MAX_TTL);
    }

    #[test]
    fn ttl_outside_its_bounds_is_rejected() {
        assert!(parse_ttl("23h").is_err());
        assert!(parse_ttl("31d").is_err());
    }

    #[test]
    fn unparseable_ttl_is_rejected() {
        assert!(parse_ttl("soon").is_err());
    }

    #[test]
    fn summary_shows_code_expiry_and_id() {
        let created = Created {
            code: "7KQ2M9XD4HPA".parse().unwrap(),
            invite: Invite {
                id: InviteId::new(&[0xa3; 32].into(), &[]),
                created_at: datetime!(2026-10-17 08:30 UTC),
                expires_at: datetime!(2026-10-18 08:30 UTC),
            },
        };

        assert_eq!(
            summary(&created),
            "Invite  7KQ2-M9XD-4HPA   expires 2026-10-18 08:30 UTC   id a3a3a"
        );
    }
}
