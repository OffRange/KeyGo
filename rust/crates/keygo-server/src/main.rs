use crate::cli::Cli;

mod api;
mod cli;
mod config;
mod db;
mod domain;
mod secrets;
mod server;
mod service;

fn main() -> anyhow::Result<()> {
    Cli::start()
}
