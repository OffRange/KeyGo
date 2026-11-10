use keygo_dpop::DpopSigner;
use std::sync::Arc;

#[derive(Debug, thiserror::Error)]
#[error("{message}")]
pub struct SignError {
    pub message: String,
}

/// The key that binds this device's tokens, held by the host
pub type SharedSigner = Arc<dyn DpopSigner<Error = SignError> + Send + Sync>;
