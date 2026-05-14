use std::time::SystemTimeError;
use thiserror::Error;
pub use totp_rs::Algorithm;
use totp_rs::{Secret, SecretParseError, TOTP, TotpUrlError};

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

pub struct TotpInfo {
    pub secret: String,
    pub issuer: Option<String>,
    pub account_name: String,
    pub algorithm: Algorithm,
    pub digits: usize,
    pub period: u64,
}

pub fn get_totp_info_from_uri(uri: String) -> Result<TotpInfo, TotpError> {
    let totp = TOTP::from_url(uri).map_err(TotpError::Url)?;
    let secret = Secret::Raw(totp.secret.clone()).to_encoded().to_string();
    Ok(TotpInfo {
        secret,
        issuer: totp.issuer.clone(),
        account_name: totp.account_name.clone(),
        algorithm: totp.algorithm,
        digits: totp.digits,
        period: totp.step,
    })
}

pub fn get_totp_url(
    algorithm: Algorithm,
    digits: usize,
    step: u64,
    secret: String,
    issuer: Option<String>,
    account_name: String,
) -> Result<String, TotpError> {
    let secret = Secret::Encoded(secret)
        .to_bytes()
        .map_err(TotpError::Secret)?;

    let totp = TOTP::new(algorithm, digits, 1, step, secret, issuer, account_name)
        .map_err(TotpError::Url)?;
    Ok(totp.get_url())
}
