use crate::config::Config;
use secrecy::ExposeSecret;
use sqlx::PgPool;
use sqlx::postgres::{PgConnectOptions, PgPoolOptions, PgSslMode};
use std::time::Duration;

pub async fn connect(config: &Config) -> anyhow::Result<PgPool> {
    let db_config = &config.database;
    let options = PgConnectOptions::new()
        .host(&db_config.host)
        .port(db_config.port)
        .database(&db_config.name)
        .username(&db_config.username)
        .password(config.db_password()?.expose_secret())
        // Plaintext is safe only because compose keeps Postgres on the internal backend network.
        // Once "bring your own db" becomes an option, we will need to add a config option to
        // toggle it. We will also need to add sqlx features to handle TLS
        .ssl_mode(PgSslMode::Disable);

    Ok(PgPoolOptions::new()
        .max_connections(10)
        .acquire_timeout(Duration::from_secs(5))
        .connect_with(options.clone())
        .await?)
}

pub async fn migrate(pool: &PgPool) -> anyhow::Result<()> {
    sqlx::migrate!().run(pool).await?;
    Ok(())
}
