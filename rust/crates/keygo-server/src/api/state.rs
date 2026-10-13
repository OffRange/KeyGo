use crate::config::Config;

#[derive(Clone)]
pub struct AppState {
    pub allow_signups: bool,
    pub opaque_state: keygo_opaque::server::ServerSetup,
}

impl AppState {
    pub fn new(config: &Config) -> anyhow::Result<Self> {
        Ok(Self {
            allow_signups: config.allow_signups,
            opaque_state: config.opaque_setup()?,
        })
    }
}
