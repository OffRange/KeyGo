pub mod create;
pub mod list;
pub mod revoke;

use crate::cli::commands::invite::create::CreateInviteArgs;
use crate::cli::commands::invite::list::ListInviteArgs;
use crate::cli::commands::invite::revoke::RevokeInviteArgs;
use crate::config::{DatabaseConfig, SecretsConfig};
use crate::db;
use crate::service::invite::Invite;
use clap::{Args, Subcommand};
use sqlx::PgPool;
use time::format_description::BorrowedFormatItem;
use time::macros::format_description;
use time::{OffsetDateTime, UtcOffset};

const UTC_MINUTES: &[BorrowedFormatItem] =
    format_description!("[year]-[month]-[day] [hour]:[minute] UTC");

#[derive(Subcommand)]
pub enum InviteCommands {
    /// Create an invite code that one registration can redeem.
    Create(CreateInviteArgs),
    /// List the open invites.
    #[command(visible_alias = "ls")]
    List(ListInviteArgs),
    /// Revoke an open invite so its code no longer works.
    Revoke(RevokeInviteArgs),
}

/// What every invite command needs to reach the database.
#[derive(Args)]
pub struct Connection {
    #[command(flatten)]
    secrets: SecretsConfig,
    #[command(flatten)]
    database: DatabaseConfig,
}

pub fn run(commands: InviteCommands) -> anyhow::Result<()> {
    match commands {
        InviteCommands::Create(args) => create::run(&args),
        InviteCommands::List(args) => list::run(&args),
        InviteCommands::Revoke(args) => revoke::run(&args),
    }
}

fn format_utc(at: OffsetDateTime) -> String {
    at.to_offset(UtcOffset::UTC)
        .format(UTC_MINUTES)
        .expect("an OffsetDateTime has every component the format needs")
}

fn id_width(invites: &[Invite]) -> usize {
    invites
        .iter()
        .map(|invite| invite.id.as_str().len())
        .max()
        .unwrap_or(0)
}

fn with_db<T>(
    connection: &Connection,
    task: impl AsyncFnOnce(&PgPool) -> anyhow::Result<T>,
) -> anyhow::Result<T> {
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?
        .block_on(async {
            let db = db::connect(&connection.database, &connection.secrets).await?;
            task(&db).await
        })
}
