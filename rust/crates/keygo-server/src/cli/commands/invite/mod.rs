pub mod create;
pub mod list;

use crate::cli::commands::invite::create::CreateInviteArgs;
use crate::config::Config;
use crate::db;
use clap::Subcommand;
use sqlx::PgPool;
use time::format_description::BorrowedFormatItem;
use time::macros::format_description;
use time::{OffsetDateTime, UtcOffset};

const UTC_MINUTES: &[BorrowedFormatItem] =
    format_description!("[year]-[month]-[day] [hour]:[minute] UTC");

#[derive(Subcommand)]
pub enum InviteCommands {
    Create(CreateInviteArgs),
    List,
}

pub fn run(config: &Config, commands: InviteCommands) -> anyhow::Result<()> {
    match commands {
        InviteCommands::Create(args) => create::run(config, &args),
        InviteCommands::List => list::run(config),
    }
}

fn format_utc(at: OffsetDateTime) -> String {
    at.to_offset(UtcOffset::UTC)
        .format(UTC_MINUTES)
        .expect("an OffsetDateTime has every component the format needs")
}

fn with_db<T>(
    config: &Config,
    task: impl AsyncFnOnce(&PgPool) -> anyhow::Result<T>,
) -> anyhow::Result<T> {
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?
        .block_on(async {
            let db = db::connect(config).await?;
            task(&db).await
        })
}
