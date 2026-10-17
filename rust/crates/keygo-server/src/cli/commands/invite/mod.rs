pub mod create;

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
}

pub fn run(config: &Config, commands: InviteCommands) -> anyhow::Result<()> {
    match commands {
        InviteCommands::Create(args) => create::run(config, &args),
    }
}
