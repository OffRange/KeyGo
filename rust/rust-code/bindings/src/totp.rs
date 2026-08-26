use std::sync::Arc;

use lib::totp::{
    TotpInfo as CoreTotpInfo, get_totp as core_get_totp,
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

impl TryFrom<lib::totp::Algorithm> for Algorithm {
    type Error = TotpError;

    /// `totp_rs::Algorithm` is `#[non_exhaustive]`, so a variant this binding
    /// does not expose stays representable no matter what we match on. Reaching
    /// one means the input named an algorithm we cannot hand to Kotlin, which
    /// is an error to report, not a reason to unwind across the FFI boundary.
    fn try_from(value: lib::totp::Algorithm) -> Result<Self, Self::Error> {
        match value {
            lib::totp::Algorithm::SHA1 => Ok(Algorithm::Sha1),
            lib::totp::Algorithm::SHA256 => Ok(Algorithm::Sha256),
            lib::totp::Algorithm::SHA512 => Ok(Algorithm::Sha512),
            _ => Err(TotpError::InvalidInput),
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
}

impl TryFrom<CoreTotpInfo> for TotpInfo {
    type Error = TotpError;

    fn try_from(value: CoreTotpInfo) -> Result<Self, Self::Error> {
        Ok(Self {
            secret: value.secret,
            issuer: value.issuer,
            account_name: value.account_name,
            algorithm: value.algorithm.try_into()?,
            digits: i32::from(value.digits),
            period: i32::try_from(value.period).map_err(|_| TotpError::InvalidInput)?,
        })
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
        let digits = u8::try_from(digits).map_err(|_| TotpError::InvalidInput)?;
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
        let digits = u8::try_from(digits).map_err(|_| TotpError::InvalidInput)?;
        let step = u64::try_from(step).map_err(|_| TotpError::InvalidInput)?;

        core_get_totp_url(algorithm.into(), digits, step, secret, issuer, account_name)
            .map_err(Into::into)
    }

    pub fn get_info_from_uri(&self, uri: String) -> Result<TotpInfo, TotpError> {
        core_get_totp_info_from_uri(uri)
            .map_err(TotpError::from)?
            .try_into()
    }
}
