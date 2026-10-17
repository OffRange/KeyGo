use crate::config::Config;
use crate::domain::InviteCode;
use anyhow::ensure;
use clap::Args;
use std::time::Duration;

#[derive(Args)]
pub struct CreateInviteArgs {
    #[arg(long, default_value = "1d", value_parser=ttl_parser)]
    ttl: Duration,
}

fn ttl_parser(s: &str) -> anyhow::Result<Duration> {
    let d = humantime::parse_duration(s)?;
    ensure!(d >= Duration::from_hours(24), "ttl must be at least 1d");
    ensure!(
        d <= Duration::from_hours(30 * 24),
        "ttl must be at most 30d"
    );
    Ok(d)
}

pub fn run(config: &Config, args: &CreateInviteArgs) -> anyhow::Result<()> {
    let code = InviteCode::generate();

    {
        let invite_pepper = config.invite_pepper()?;
        let hash = invite_pepper.hash(&code);
    }

    println!("{}", code);
    Ok(())
}
