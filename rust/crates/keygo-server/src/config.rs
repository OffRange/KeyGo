use crate::secrets::{DbPassword, OpaqueSetup, SecretStore};
use clap::{Args, ValueEnum};
use keygo_opaque::server::ServerSetup;
use secrecy::SecretString;
use std::path::PathBuf;

#[derive(Args)]
pub struct Config {
    #[arg(
        long,
        env = "KEYGO_ENVIRONMENT",
        value_enum,
        default_value_t,
        global = true
    )]
    pub environment: Environment,

    #[arg(
        long,
        env = "KEYGO_PUBLIC_URL",
        default_value = "http://localhost",
        global = true
    )]
    pub public_url: String,

    #[arg(long, env = "KEYGO_ALLOW_SIGNUPS", global = true)]
    pub allow_signups: bool,

    /// Directory with one file per secret. Compose mounts its secrets at /run/secrets.
    #[arg(
        long,
        env = "KEYGO_SECRETS_DIR",
        default_value = "/run/secrets",
        global = true
    )]
    pub secrets_dir: PathBuf,

    #[command(flatten, next_help_heading = "Database")]
    pub database: DatabaseConfig,
}

#[derive(Args)]
pub struct DatabaseConfig {
    /// Full connection URL. When set, it replaces the options below and the db_password secret.
    /// Meant for local development, where `make serve` sets it.
    #[arg(
        long = "db-url",
        env = "KEYGO_DATABASE_URL",
        hide_env_values = true,
        global = true
    )]
    pub url: Option<SecretString>,

    #[arg(
        long = "db-host",
        env = "KEYGO_DATABASE_HOST",
        default_value = "postgres",
        global = true
    )]
    pub host: String,

    #[arg(
        long = "db-port",
        env = "KEYGO_DATABASE_PORT",
        default_value_t = 5432,
        global = true
    )]
    pub port: u16,

    #[arg(
        long = "db-name",
        env = "KEYGO_DATABASE_NAME",
        default_value = "keygo",
        global = true
    )]
    pub name: String,

    #[arg(
        long = "db-user",
        env = "KEYGO_DATABASE_USERNAME",
        default_value = "keygo",
        global = true
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

impl Config {
    pub(crate) fn opaque_setup(&self) -> anyhow::Result<ServerSetup> {
        SecretStore::new(&self.secrets_dir).read::<OpaqueSetup>()
    }
    pub(crate) fn db_password(&self) -> anyhow::Result<SecretString> {
        SecretStore::new(&self.secrets_dir).read::<DbPassword>()
    }
}
