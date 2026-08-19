use std::sync::Arc;

use lib::totp::{
    SecretStrength as CoreSecretStrength, TotpInfo as CoreTotpInfo, get_totp as core_get_totp,
    get_totp_info_from_uri as core_get_totp_info_from_uri, get_totp_url as core_get_totp_url,
};
use thiserror::Error;

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum Algorithm {
    Sha1,
    Sha256,
    Sha512,
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

impl From<lib::totp::Algorithm> for Algorithm {
    fn from(value: lib::totp::Algorithm) -> Self {
        match value {
            lib::totp::Algorithm::SHA1 => Algorithm::Sha1,
            lib::totp::Algorithm::SHA256 => Algorithm::Sha256,
            lib::totp::Algorithm::SHA512 => Algorithm::Sha512,
        }
    }
}

/// Whether a TOTP secret reaches the 128 bits RFC 4226 asks for. Shorter
/// secrets are accepted anyway, because services like GitHub issue them.
#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum SecretStrength {
    Trustworthy,
    Untrusted,
}

impl From<CoreSecretStrength> for SecretStrength {
    fn from(value: CoreSecretStrength) -> Self {
        match value {
            CoreSecretStrength::Trustworthy => Self::Trustworthy,
            CoreSecretStrength::Untrusted => Self::Untrusted,
        }
    }
}

#[derive(Debug, uniffi::Record)]
pub struct TotpInfo {
    pub secret: String,
    pub issuer: Option<String>,
    pub account_name: String,
    pub algorithm: Algorithm,
    pub digits: i32,
    pub period: i32,
    pub strength: SecretStrength,
}

impl From<CoreTotpInfo> for TotpInfo {
    fn from(value: CoreTotpInfo) -> Self {
        Self {
            secret: value.secret,
            issuer: value.issuer,
            account_name: value.account_name,
            algorithm: value.algorithm.into(),
            digits: value.digits as i32,
            period: value.period as i32,
            strength: value.strength.into(),
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

    pub fn get_url(
        &self,
        algorithm: Algorithm,
        digits: i32,
        step: i32,
        secret: String,
        issuer: Option<String>,
        account_name: String,
    ) -> Result<String, TotpError> {
        let digits = usize::try_from(digits).map_err(|_| TotpError::InvalidInput)?;
        let step = u64::try_from(step).map_err(|_| TotpError::InvalidInput)?;

        core_get_totp_url(algorithm.into(), digits, step, secret, issuer, account_name)
            .map_err(Into::into)
    }

    pub fn get_info_from_uri(&self, uri: String) -> Result<TotpInfo, TotpError> {
        core_get_totp_info_from_uri(uri)
            .map(Into::into)
            .map_err(Into::into)
    }
}
