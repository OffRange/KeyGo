use crate::cli::Cli;

mod api;
mod cli;
mod config;
mod secrets;
mod server;

fn main() -> anyhow::Result<()> {
    Cli::start()
}
