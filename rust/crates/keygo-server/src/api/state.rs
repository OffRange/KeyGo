use crate::cache::LoginStates;
use crate::config::DeploymentConfig;
use crate::domain::TokenIssuer;
use crate::service::registration::Signup;
use anyhow::Context;
use keygo_dpop::verify::DpopVerifier;
use sqlx::PgPool;
use std::sync::Arc;
use url::Url;

#[derive(Clone)]
pub struct AppState {
    pub public_url: Url,
    pub signup: Signup,
    pub opaque_state: keygo_opaque::server::ServerSetup,
    pub db: PgPool,
    pub login_states: LoginStates,
    pub dpop_verifier: Arc<DpopVerifier>,
    pub token_issuer: Arc<TokenIssuer>,
}

impl AppState {
    pub fn new(config: &DeploymentConfig, db: PgPool) -> anyhow::Result<Self> {
        let secrets = &config.secrets;
        let public_url = Url::parse(&config.server.public_url).context("invalid public url")?;
        let issuer = public_url.as_str().trim_end_matches('/').to_owned();
        Ok(Self {
            public_url,
            signup: if config.server.allow_signups {
                Signup::Open
            } else {
                Signup::InviteOnly(Arc::new(secrets.invite_pepper()?))
            },
            opaque_state: secrets.opaque_setup()?,
            db,
            login_states: LoginStates::new(),
            dpop_verifier: Arc::new(DpopVerifier::new(secrets.dpop_nonce_key()?)),
            token_issuer: Arc::new(TokenIssuer::new(secrets.access_token_key()?, issuer)),
        })
    }
}
