use std::time::SystemTimeError;
use thiserror::Error;
pub use totp_rs::Algorithm;
use totp_rs::{Secret, SecretParseError, TotpUrlError, TOTP};

#[derive(Debug, Error)]
pub enum TotpError {
    #[error("url error: {0}")]
    Url(TotpUrlError),
    #[error("time error: {0}")]
    Time(SystemTimeError),
    #[error("secret parse error: {0}")]
    Secret(SecretParseError),
}

pub fn get_totp(
    algorithm: Algorithm,
    digits: usize,
    step: u64,
    secret: String,
) -> Result<String, TotpError> {
    let secret = Secret::Encoded(secret)
        .to_bytes()
        .map_err(TotpError::Secret)?;
    let totp = TOTP::new(algorithm, digits, 1, step, secret, None, "".to_string())
        .map_err(TotpError::Url)?;
    totp.generate_current().map_err(TotpError::Time)
}
