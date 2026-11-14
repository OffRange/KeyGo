use keygo_server::cli::Cli;
use std::process::ExitCode;

fn main() -> ExitCode {
    match Cli::start() {
        Ok(()) => ExitCode::SUCCESS,
        Err(err) => {
            eprintln!("error: {err:?}");
            ExitCode::FAILURE
        }
    }
}
