use std::sync::Arc;

use lib::totp::get_totp as core_get_totp;
use thiserror::Error;

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum Algorithm {
    Sha1,
    Sha256,
    Sha512,
}

#[uniffi::export]
pub fn algorithm_from_string(value: String) -> Result<Algorithm, TotpError> {
    match value.trim().to_ascii_lowercase().as_str() {
        "sha1" | "sha-1" => Ok(Algorithm::Sha1),
        "sha256" | "sha-256" => Ok(Algorithm::Sha256),
        "sha512" | "sha-512" => Ok(Algorithm::Sha512),
        _ => Err(TotpError::Generic(format!(
            "unsupported algorithm: {}",
            value
        ))),
    }
}

impl From<Algorithm> for lib::totp::Algorithm {
    fn from(value: Algorithm) -> Self {
        match value {
            Algorithm::Sha1 => lib::totp::Algorithm::SHA1,
            Algorithm::Sha256 => lib::totp::Algorithm::SHA256,
            Algorithm::Sha512 => lib::totp::Algorithm::SHA512,
        }
    }
}

#[derive(Debug, Error, uniffi::Error)]
pub enum TotpError {
    #[error("{0}")]
    Generic(String),

    #[error("Invalid input parameter")]
    InvalidInput,
}

impl From<lib::totp::TotpError> for TotpError {
    fn from(err: lib::totp::TotpError) -> Self {
        Self::Generic(err.to_string())
    }
}

#[derive(uniffi::Object)]
pub struct TotpService;

#[uniffi::export]
impl TotpService {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn get_totp(
        &self,
        algorithm: Algorithm,
        digits: i32,
        step: i32,
        secret: String,
    ) -> Result<String, TotpError> {
        let digits = usize::try_from(digits).map_err(|_| TotpError::InvalidInput)?;
        let step = u64::try_from(step).map_err(|_| TotpError::InvalidInput)?;
        core_get_totp(algorithm.into(), digits, step, secret).map_err(Into::into)
    }
}
