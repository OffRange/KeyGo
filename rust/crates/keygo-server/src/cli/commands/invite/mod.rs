pub mod create;

use crate::cli::commands::invite::create::CreateInviteArgs;
use crate::config::Config;
use clap::Subcommand;

#[derive(Subcommand)]
pub enum InviteCommands {
    Create(CreateInviteArgs),
}

pub fn run(config: &Config, commands: InviteCommands) -> anyhow::Result<()> {
    match commands {
        InviteCommands::Create(args) => create::run(config, &args),
    }
}
