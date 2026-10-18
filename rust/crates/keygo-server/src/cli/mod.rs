mod commands;

use crate::cli::commands::check::CheckArgs;
use crate::cli::commands::init::InitArgs;
use crate::cli::commands::invite::InviteCommands;
use crate::cli::commands::serve::ServeArgs;
use clap::{Parser, Subcommand};

#[derive(Parser)]
#[command(name = "keygo-server", version, about)]
pub struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Generate the secrets the server needs. Existing secrets are kept.
    Init(InitArgs),
    /// Validate the configuration and secrets without starting the server.
    Check(CheckArgs),
    /// Run the API server.
    Serve(ServeArgs),
    /// Manage the invite codes that registration asks for.
    #[command(subcommand)]
    Invite(InviteCommands),
}

impl Cli {
    pub fn start() -> anyhow::Result<()> {
        match Cli::parse().command {
            Commands::Init(args) => commands::init::run(&args),
            Commands::Check(args) => commands::check::run(&args),
            Commands::Serve(args) => commands::serve::run(&args),
            Commands::Invite(commands) => commands::invite::run(commands),
        }
    }
}
