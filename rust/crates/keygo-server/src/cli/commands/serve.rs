use crate::config::DeploymentConfig;
use crate::server;
use clap::Args;
use std::net::SocketAddr;
use tracing_subscriber::EnvFilter;

#[derive(Args)]
pub struct ServeArgs {
    /// Address the API listens on.
    #[arg(long, env = "KEYGO_LISTEN", default_value = "0.0.0.0:8000")]
    listen: SocketAddr,
    #[command(flatten)]
    config: DeploymentConfig,
}

pub fn run(args: &ServeArgs) -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::try_from_env("KEYGO_LOG").unwrap_or_else(|_| "info".into()))
        .init();

    tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?
        .block_on(server::start(&args.config, args.listen))
}
