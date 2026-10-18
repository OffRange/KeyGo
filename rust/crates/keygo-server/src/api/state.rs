use crate::config::DeploymentConfig;
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
    pub fn new(config: &DeploymentConfig, db: PgPool) -> anyhow::Result<Self> {
        let secrets = &config.secrets;
        Ok(Self {
            signup: if config.server.allow_signups {
                Signup::Open
            } else {
                Signup::InviteOnly(Arc::new(secrets.invite_pepper()?))
            },
            opaque_state: secrets.opaque_setup()?,
            db,
        })
    }
}
