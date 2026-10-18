use crate::config::Config;
use crate::service::registration::Signup;
use sqlx::PgPool;
use std::sync::Arc;

#[derive(Clone)]
pub struct AppState {
    pub signup: Signup,
    pub opaque_state: keygo_opaque::server::ServerSetup,
    pub db: PgPool,
}

impl AppState {
    pub fn new(config: &Config, db: PgPool) -> anyhow::Result<Self> {
        Ok(Self {
            signup: if config.allow_signups {
                Signup::Open
            } else {
                Signup::InviteOnly(Arc::new(config.invite_pepper()?))
            },
            opaque_state: config.opaque_setup()?,
            db,
        })
    }
}
