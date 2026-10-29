pub mod devices;
pub mod invites;
pub mod opaque_records;
pub mod refresh_tokens;
pub mod registrations;
pub mod sessions;
pub mod users;

use crate::config::{DatabaseConfig, SecretsConfig};
use crate::domain::RevokeReason;
use secrecy::ExposeSecret;
use sqlx::PgPool;
use sqlx::postgres::{PgConnectOptions, PgPoolOptions, PgSslMode};
use std::str::FromStr;
use std::time::Duration;

pub async fn connect(database: &DatabaseConfig, secrets: &SecretsConfig) -> anyhow::Result<PgPool> {
    Ok(PgPoolOptions::new()
        .max_connections(10)
        .acquire_timeout(Duration::from_secs(5))
        .connect_with(connect_options(database, secrets)?)
        .await?)
}

fn connect_options(
    database: &DatabaseConfig,
    secrets: &SecretsConfig,
) -> anyhow::Result<PgConnectOptions> {
    if let Some(url) = &database.url {
        return Ok(PgConnectOptions::from_str(url.expose_secret())?);
    }

    Ok(PgConnectOptions::new()
        .host(&database.host)
        .port(database.port)
        .database(&database.name)
        .username(&database.username)
        .password(secrets.db_password()?.expose_secret())
        // Plaintext is safe only because compose keeps Postgres on the internal backend network.
        // Once "bring your own db" becomes an option, we will need to add a config option to
        // toggle it. We will also need to add sqlx features to handle TLS
        .ssl_mode(PgSslMode::Disable))
}

pub async fn migrate(pool: &PgPool) -> anyhow::Result<()> {
    sqlx::migrate!().run(pool).await?;
    Ok(())
}

/// Reports a stored value that no longer decodes. Every writer stores only valid values, so this
/// means a corrupted row.
fn corrupt(err: impl Into<sqlx::error::BoxDynError>) -> sqlx::Error {
    sqlx::Error::Decode(err.into())
}

fn revoke_reason(stored: Option<String>) -> sqlx::Result<Option<RevokeReason>> {
    stored
        .as_deref()
        .map(str::parse)
        .transpose()
        .map_err(corrupt)
}
