use crate::config::Config;
use secrecy::ExposeSecret;
use sqlx::PgPool;
use sqlx::postgres::{PgConnectOptions, PgPoolOptions, PgSslMode};
use std::str::FromStr;
use std::time::Duration;

pub async fn connect(config: &Config) -> anyhow::Result<PgPool> {
    Ok(PgPoolOptions::new()
        .max_connections(10)
        .acquire_timeout(Duration::from_secs(5))
        .connect_with(connect_options(config)?)
        .await?)
}

fn connect_options(config: &Config) -> anyhow::Result<PgConnectOptions> {
    let db_config = &config.database;
    if let Some(url) = &db_config.url {
        return Ok(PgConnectOptions::from_str(url.expose_secret())?);
    }

    Ok(PgConnectOptions::new()
        .host(&db_config.host)
        .port(db_config.port)
        .database(&db_config.name)
        .username(&db_config.username)
        .password(config.db_password()?.expose_secret())
        // Plaintext is safe only because compose keeps Postgres on the internal backend network.
        // Once "bring your own db" becomes an option, we will need to add a config option to
        // toggle it. We will also need to add sqlx features to handle TLS
        .ssl_mode(PgSslMode::Disable))
}

pub async fn migrate(pool: &PgPool) -> anyhow::Result<()> {
    sqlx::migrate!().run(pool).await?;
    Ok(())
}
