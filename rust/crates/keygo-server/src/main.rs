use crate::cli::Cli;

mod cli;
mod config;
mod secrets;

fn main() -> anyhow::Result<()> {
    Cli::start()
}
