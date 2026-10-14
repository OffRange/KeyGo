use crate::config::Config;
use sqlx::PgPool;

#[derive(Clone)]
pub struct AppState {
    pub allow_signups: bool,
    pub opaque_state: keygo_opaque::server::ServerSetup,
    pub db: PgPool,
}

impl AppState {
    pub fn new(config: &Config, db: PgPool) -> anyhow::Result<Self> {
        Ok(Self {
            allow_signups: config.allow_signups,
            opaque_state: config.opaque_setup()?,
            db,
        })
    }
}
