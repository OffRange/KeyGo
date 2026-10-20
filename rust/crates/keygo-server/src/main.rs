use crate::cli::Cli;
use std::process::ExitCode;

mod api;
mod cache;
mod cli;
mod config;
mod db;
mod domain;
mod secrets;
mod server;
mod service;
#[cfg(test)]
mod testing;

fn main() -> ExitCode {
    match Cli::start() {
        Ok(()) => ExitCode::SUCCESS,
        Err(err) => {
            eprintln!("error: {err:?}");
            ExitCode::FAILURE
        }
    }
}
