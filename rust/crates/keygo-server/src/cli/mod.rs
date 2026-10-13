mod commands;

use crate::cli::commands::serve::ServeArgs;
use crate::config::Config;
use clap::{Parser, Subcommand};

#[derive(Parser)]
#[command(name = "keygo-server", version, about)]
pub struct Cli {
    #[command(flatten)]
    config: Config,

    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    Init,
    Check,
    Serve(ServeArgs),
}

impl Cli {
    pub fn start() -> anyhow::Result<()> {
        let cli = Cli::parse();

        match cli.command {
            Commands::Init => commands::init::run(&cli.config),
            Commands::Check => commands::check::run(&cli.config),
            Commands::Serve(args) => commands::serve::run(&cli.config, &args),
        }
    }
}
