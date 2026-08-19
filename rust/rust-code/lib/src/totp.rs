use std::ops::RangeInclusive;
use std::time::SystemTimeError;
use thiserror::Error;
pub use totp_rs::Algorithm;
use totp_rs::{Secret, SecretParseError, TOTP, TotpUrlError};

/// Secret size RFC 4226 asks for, in bytes (128 bits).
const MIN_SECRET_BYTES: usize = 16;

/// Digit counts RFC 6238 permits.
const ALLOWED_DIGITS: RangeInclusive<usize> = 6..=8;

#[derive(Debug, Error)]
pub enum TotpError {
    #[error("url error: {0}")]
    Url(TotpUrlError),
    #[error("time error: {0}")]
    Time(SystemTimeError),
    #[error("secret parse error: {0}")]
    Secret(SecretParseError),
}

/// Whether a secret reaches [`MIN_SECRET_BYTES`]. Shorter ones stay usable,
/// because services like GitHub issue them, so callers can warn instead.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SecretStrength {
    Trustworthy,
    Untrusted,
}

impl SecretStrength {
    fn of(secret: &[u8]) -> Self {
        if secret.len() >= MIN_SECRET_BYTES {
            Self::Trustworthy
        } else {
            Self::Untrusted
        }
    }
}

/// The checks `TOTP::new` runs, minus its 128 bit secret floor. The unchecked
/// constructors skip all of them, so every caller runs this instead.
fn validate(totp: &TOTP) -> Result<(), TotpError> {
    if totp.secret.is_empty() {
        return Err(TotpError::Url(TotpUrlError::SecretSize(0)));
    }

    if !ALLOWED_DIGITS.contains(&totp.digits) {
        return Err(TotpError::Url(TotpUrlError::DigitsNumber(totp.digits)));
    }

    if let Some(issuer) = totp.issuer.as_deref().filter(|it| it.contains(':')) {
        return Err(TotpError::Url(TotpUrlError::Issuer(issuer.to_string())));
    }

    if totp.account_name.contains(':') {
        return Err(TotpError::Url(TotpUrlError::AccountName(
            totp.account_name.clone(),
        )));
    }

    Ok(())
}

fn build_totp(
    algorithm: Algorithm,
    digits: usize,
    step: u64,
    secret: String,
    issuer: Option<String>,
    account_name: String,
) -> Result<TOTP, TotpError> {
    let secret = Secret::Encoded(secret)
        .to_bytes()
        .map_err(TotpError::Secret)?;
    let totp = TOTP::new_unchecked(algorithm, digits, 1, step, secret, issuer, account_name);
    validate(&totp)?;

    Ok(totp)
}

pub fn get_totp(
    algorithm: Algorithm,
    digits: usize,
    step: u64,
    secret: String,
) -> Result<String, TotpError> {
    let totp = build_totp(algorithm, digits, step, secret, None, "".to_string())?;
    totp.generate_current().map_err(TotpError::Time)
}

pub struct TotpInfo {
    pub secret: String,
    pub issuer: Option<String>,
    pub account_name: String,
    pub algorithm: Algorithm,
    pub digits: usize,
    pub period: u64,
    pub strength: SecretStrength,
}

pub fn get_totp_info_from_uri(uri: String) -> Result<TotpInfo, TotpError> {
    let totp = TOTP::from_url_unchecked(uri).map_err(TotpError::Url)?;
    validate(&totp)?;

    Ok(TotpInfo {
        secret: Secret::Raw(totp.secret.clone()).to_encoded().to_string(),
        issuer: totp.issuer.clone(),
        account_name: totp.account_name.clone(),
        algorithm: totp.algorithm,
        digits: totp.digits,
        period: totp.step,
        strength: SecretStrength::of(&totp.secret),
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
    let totp = build_totp(algorithm, digits, step, secret, issuer, account_name)?;
    Ok(totp.get_url())
}

pub(crate) fn is_valid_totp_secret(s: &str) -> bool {
    base32::decode(base32::Alphabet::Rfc4648 { padding: false }, s).is_some()
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 80 bits, the kind of secret services like GitHub hand out.
    const SHORT_SECRET: &str = "JBSWY3DPEHPK3PXP";
    /// 128 bits, the minimum RFC 4226 asks for.
    const LONG_SECRET: &str = "JBSWY3DPEHPK3PXPJBSWY3DPEH";

    fn uri(secret: &str) -> String {
        format!("otpauth://totp/GitHub:alice?secret={secret}&issuer=GitHub")
    }

    fn url_with_issuer(issuer: &str) -> Result<String, TotpError> {
        get_totp_url(
            Algorithm::SHA1,
            6,
            30,
            SHORT_SECRET.to_string(),
            Some(issuer.to_string()),
            "alice".to_string(),
        )
    }

    #[test]
    fn generates_code_for_secret_below_128_bits() {
        let code = get_totp(Algorithm::SHA1, 6, 30, SHORT_SECRET.to_string()).unwrap();
        assert_eq!(6, code.len());
    }

    #[test]
    fn reports_strength_of_parsed_secret() {
        let short = get_totp_info_from_uri(uri(SHORT_SECRET)).unwrap();
        let long = get_totp_info_from_uri(uri(LONG_SECRET)).unwrap();

        assert_eq!(SecretStrength::Untrusted, short.strength);
        assert_eq!(SecretStrength::Trustworthy, long.strength);
        assert_eq!(SHORT_SECRET, short.secret);
    }

    #[test]
    fn builds_url_for_secret_below_128_bits() {
        let url = url_with_issuer("GitHub").unwrap();

        assert!(url.contains(SHORT_SECRET), "unexpected url: {url}");
    }

    #[test]
    fn rejects_empty_secret() {
        assert!(get_totp(Algorithm::SHA1, 6, 30, "".to_string()).is_err());
    }

    #[test]
    fn rejects_digits_outside_rfc_range() {
        assert!(get_totp(Algorithm::SHA1, 9, 30, SHORT_SECRET.to_string()).is_err());
    }

    #[test]
    fn rejects_colon_in_issuer() {
        assert!(url_with_issuer("Git:Hub").is_err());
    }

    #[test]
    fn rejects_uri_without_secret() {
        assert!(get_totp_info_from_uri(uri("")).is_err());
    }

    #[test]
    fn rejects_secret_that_is_not_base32() {
        assert!(get_totp(Algorithm::SHA1, 6, 30, "not base32!".to_string()).is_err());
    }
}
