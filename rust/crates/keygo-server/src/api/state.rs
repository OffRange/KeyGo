use crate::config::Config;

#[derive(Clone)]
pub struct AppState {
    pub allow_signups: bool,
}

impl AppState {
    pub fn new(config: &Config) -> Self {
        Self {
            allow_signups: config.allow_signups,
        }
    }
}
