use crate::api::{self, AppState};
use crate::config::DeploymentConfig;
use crate::db;
use anyhow::Context;
use std::net::SocketAddr;
use tokio::net::TcpListener;
use tokio::signal::unix::{SignalKind, signal};

mod sweeper;

pub async fn start(config: &DeploymentConfig, listen: SocketAddr) -> anyhow::Result<()> {
    let db = db::connect(&config.database, &config.secrets).await?;
    db::migrate(&db).await?;

    let sweeper = tokio::spawn(sweeper::run(db.clone()));
    let app = api::router(AppState::new(config, db)?);
    let listener = TcpListener::bind(listen)
        .await
        .with_context(|| format!("cannot listen on {listen}"))?;
    tracing::info!(address = %listener.local_addr()?, "listening");

    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await?;
    sweeper.abort();
    tracing::info!("stopped");
    Ok(())
}

async fn shutdown_signal() {
    let terminate = async {
        match signal(SignalKind::terminate()) {
            Ok(mut terminate) => {
                terminate.recv().await;
            }
            Err(_) => std::future::pending().await,
        }
    };

    tokio::select! {
        _ = tokio::signal::ctrl_c() => {}
        _ = terminate => {}
    }
}
