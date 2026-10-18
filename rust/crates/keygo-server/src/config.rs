use crate::domain::InvitePepper;
use crate::secrets::{DbPassword, OpaqueSetup, SecretStore};
use clap::{Args, ValueEnum};
use keygo_opaque::server::ServerSetup;
use secrecy::SecretString;
use std::path::PathBuf;

/// Everything `serve` reads, so `check` validates exactly what the server will run with.
#[derive(Args)]
pub struct DeploymentConfig {
    #[command(flatten)]
    pub server: ServerConfig,
    #[command(flatten)]
    pub secrets: SecretsConfig,
    #[command(flatten)]
    pub database: DatabaseConfig,
}

#[derive(Args)]
#[command(next_help_heading = "Server")]
pub struct ServerConfig {
    /// Deployment environment.
    #[arg(long, env = "KEYGO_ENVIRONMENT", value_enum, default_value_t)]
    pub environment: Environment,
    /// URL that clients reach the server at.
    #[arg(long, env = "KEYGO_PUBLIC_URL", default_value = "http://localhost")]
    pub public_url: String,
    /// Let anyone register without an invite.
    #[arg(long, env = "KEYGO_ALLOW_SIGNUPS")]
    pub allow_signups: bool,
}

#[derive(Args)]
#[command(next_help_heading = "Secrets")]
pub struct SecretsConfig {
    /// Directory with one file per secret.
    #[arg(
        long = "secrets-dir",
        value_name = "DIR",
        env = "KEYGO_SECRETS_DIR",
        default_value = "/run/secrets"
    )]
    pub dir: PathBuf,
}

#[derive(Args)]
#[command(next_help_heading = "Database")]
pub struct DatabaseConfig {
    /// Connection URL. Replaces the options below and the db_password secret.
    #[arg(long = "db-url", env = "KEYGO_DATABASE_URL", hide_env_values = true)]
    pub url: Option<SecretString>,
    /// Postgres host.
    #[arg(
        long = "db-host",
        env = "KEYGO_DATABASE_HOST",
        default_value = "postgres"
    )]
    pub host: String,
    /// Postgres port.
    #[arg(long = "db-port", env = "KEYGO_DATABASE_PORT", default_value_t = 5432)]
    pub port: u16,
    /// Database name.
    #[arg(long = "db-name", env = "KEYGO_DATABASE_NAME", default_value = "keygo")]
    pub name: String,
    /// Database user. Its password is the db_password secret.
    #[arg(
        long = "db-user",
        value_name = "USER",
        env = "KEYGO_DATABASE_USERNAME",
        default_value = "keygo"
    )]
    pub username: String,
}

#[derive(ValueEnum, Clone, Copy, Default, Debug, PartialEq, Eq)]
pub enum Environment {
    #[value(alias = "prod")]
    Production,
    #[default]
    #[value(alias = "dev")]
    Development,
}

impl Environment {
    pub fn name(&self) -> &'static str {
        match self {
            Environment::Production => "production",
            Environment::Development => "development",
        }
    }
}

impl SecretsConfig {
    pub(crate) fn store(&self) -> SecretStore {
        SecretStore::new(&self.dir)
    }

    pub(crate) fn opaque_setup(&self) -> anyhow::Result<ServerSetup> {
        self.store().read::<OpaqueSetup>()
    }

    pub(crate) fn db_password(&self) -> anyhow::Result<SecretString> {
        self.store().read::<DbPassword>()
    }

    pub(crate) fn invite_pepper(&self) -> anyhow::Result<InvitePepper> {
        self.store().read::<InvitePepper>()
    }
}
