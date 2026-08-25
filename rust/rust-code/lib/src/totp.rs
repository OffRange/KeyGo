use thiserror::Error;
pub use totp_rs::Algorithm;
use totp_rs::{Builder, Secret, SecretParseError, Totp, TotpError as TOTPError};

/// A secret long enough to clear `Builder::build`'s 128 bit floor, swapped in
/// for the real one while validating. Plenty of providers hand out 80 bit
/// secrets and refusing those would make the app useless for them, so that
/// floor is the one rule we drop.
const SECRET_FLOOR_STANDIN: [u8; 16] = [0; 16];

#[derive(Debug, Error)]
pub enum TotpError {
    #[error("url error: {0}")]
    Url(TOTPError),
    #[error("secret parse error: {0}")]
    Secret(SecretParseError),
}

/// Every check `Builder::build` runs, with its 128 bit secret floor replaced by
/// "the secret must not be empty".
///
/// The checks are deliberately not restated here. Copying them means the copy
/// silently rots whenever totp-rs adds one, which is exactly how the
/// step-of-zero check 6.0 introduced went missing: a `period=0` URI parsed
/// fine, and generating a code from it divided by zero. Building against a
/// stand-in secret keeps the rules the library's to define, including any it
/// adds later.
fn validate(totp: &Totp) -> Result<(), TotpError> {
    if totp.secret().is_empty() {
        return Err(TotpError::Url(TOTPError::SecretTooShort { bits: 0 }));
    }

    Builder::new()
        .with_algorithm(totp.algorithm())
        .with_digits(totp.digits())
        .with_skew(totp.skew())
        .with_step_duration(totp.step())
        .with_secret(SECRET_FLOOR_STANDIN)
        .with_issuer(totp.issuer())
        .with_account_name(totp.account_name())
        .build()
        .map(|_| ())
        .map_err(TotpError::Url)
}

fn build_totp(
    algorithm: Algorithm,
    digits: u8,
    step: u64,
    secret: String,
    issuer: Option<String>,
    account_name: String,
) -> Result<Totp, TotpError> {
    let secret = Secret::try_from_base32(secret).map_err(TotpError::Secret)?;
    let totp = Builder::new()
        .with_algorithm(algorithm)
        .with_digits(digits)
        .with_skew(1)
        .with_step_duration(step)
        .with_secret(secret)
        .with_issuer(issuer)
        .with_account_name(account_name)
        .build_noncompliant();
    validate(&totp)?;

    Ok(totp)
}

pub fn get_totp(
    algorithm: Algorithm,
    digits: u8,
    step: u64,
    secret: String,
) -> Result<String, TotpError> {
    let totp = build_totp(algorithm, digits, step, secret, None, "".to_string())?;
    Ok(totp.generate_current().to_string())
}

pub struct TotpInfo {
    pub secret: String,
    pub issuer: Option<String>,
    pub account_name: String,
    pub algorithm: Algorithm,
    pub digits: u8,
    pub period: u64,
}

pub fn get_totp_info_from_uri(uri: String) -> Result<TotpInfo, TotpError> {
    let totp = Totp::from_url_unchecked(uri).map_err(TotpError::Url)?;
    validate(&totp)?;

    Ok(TotpInfo {
        secret: totp.secret().to_base32(),
        issuer: totp.issuer().map(String::from),
        account_name: totp.account_name().to_string(),
        algorithm: totp.algorithm(),
        digits: totp.digits(),
        period: totp.step(),
    })
}

pub fn get_totp_url(
    algorithm: Algorithm,
    digits: u8,
    step: u64,
    secret: String,
    issuer: Option<String>,
    account_name: String,
) -> Result<String, TotpError> {
    let totp = build_totp(algorithm, digits, step, secret, issuer, account_name)?;
    totp.to_url().map_err(TotpError::Url)
}

pub(crate) fn is_valid_totp_secret(s: &str) -> bool {
    base32::decode(base32::Alphabet::Rfc4648 { padding: false }, s).is_some()
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 80 bits, the kind of secret services like GitHub hand out.
    const SHORT_SECRET: &str = "JBSWY3DPEHPK3PXP";
    /// 160 bits, the length RFC 4226 recommends.
    const LONG_SECRET: &str = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

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
    fn parses_uri_regardless_of_secret_length() {
        let short = get_totp_info_from_uri(uri(SHORT_SECRET)).unwrap();
        let long = get_totp_info_from_uri(uri(LONG_SECRET)).unwrap();

        assert_eq!(SHORT_SECRET, short.secret);
        assert_eq!(LONG_SECRET, long.secret);
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

    #[test]
    fn rejects_step_of_zero() {
        assert!(get_totp(Algorithm::SHA1, 6, 0, SHORT_SECRET.to_string()).is_err());
    }

    /// A step of zero used to reach `generate`, where dividing the timestamp by
    /// it panicked. Anyone who can hand the app a URI picks the period, so this
    /// has to fail as an error rather than take the process down.
    #[test]
    fn rejects_uri_with_period_of_zero() {
        let uri = format!("otpauth://totp/GitHub:alice?secret={SHORT_SECRET}&period=0");

        assert!(get_totp_info_from_uri(uri).is_err());
    }

    /// totp-rs 6.0 made an empty account name an error rather than emitting
    /// `otpauth://totp/?secret=...` the way 5.x did. The label is required by
    /// the otpauth format, so the caller has to supply one.
    #[test]
    fn requires_account_name_for_url() {
        let url = get_totp_url(
            Algorithm::SHA1,
            6,
            30,
            SHORT_SECRET.to_string(),
            Some("GitHub".to_string()),
            "".to_string(),
        );

        assert!(url.is_err());
    }

    /// The RFC 6238 appendix B vectors, which pin the generated codes to known
    /// answers across upgrades of the hashing crates underneath totp-rs.
    #[test]
    fn matches_rfc6238_test_vectors() {
        const TIMES: [u64; 6] = [
            59,
            1111111109,
            1111111111,
            1234567890,
            2000000000,
            20000000000,
        ];

        let cases = [
            (
                Algorithm::SHA1,
                "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
                [
                    "94287082", "07081804", "14050471", "89005924", "69279037", "65353130",
                ],
            ),
            (
                Algorithm::SHA256,
                "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA",
                [
                    "46119246", "68084774", "67062674", "91819424", "90698825", "77737706",
                ],
            ),
            (
                Algorithm::SHA512,
                "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA",
                [
                    "90693936", "25091201", "99943326", "93441116", "38618901", "47863826",
                ],
            ),
        ];

        for (algorithm, secret, expected) in cases {
            let totp = build_totp(
                algorithm,
                8,
                30,
                secret.to_string(),
                None,
                "alice".to_string(),
            )
                .unwrap();

            for (time, expected) in TIMES.iter().zip(expected) {
                assert_eq!(
                    expected,
                    totp.generate(*time).to_string(),
                    "{algorithm:?} at t={time}",
                );
            }
        }
    }
}
