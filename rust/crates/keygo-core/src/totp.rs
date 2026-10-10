use thiserror::Error;
pub use totp_rs::Algorithm;
use totp_rs::{Builder, Secret, SecretParseError, Totp};

/// A secret long enough to clear `Builder::build`'s 128 bit floor, swapped in
/// for the real one while validating. Plenty of providers hand out 80 bit
/// secrets and refusing those would make the app useless for them, so that
/// floor is the one rule we drop.
const SECRET_FLOOR_STANDIN: [u8; 16] = [0; 16];

#[derive(Debug, Error)]
pub enum TotpError {
    #[error("invalid totp: {0}")]
    Invalid(totp_rs::TotpError),
    #[error("invalid secret: {0}")]
    Secret(SecretParseError),
}

/// How codes are generated from a secret.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct TotpParams {
    pub algorithm: Algorithm,
    pub digits: u8,
    /// Seconds each code is valid for.
    pub period: u64,
}

pub struct TotpInfo {
    /// Base32, as it appears in an `otpauth://` URI.
    pub secret: String,
    pub issuer: Option<String>,
    pub account_name: String,
    pub params: TotpParams,
}

/// The current code for a base32 `secret`.
pub fn generate(params: TotpParams, secret: &str) -> Result<String, TotpError> {
    Ok(build(params, secret, None, "")?
        .generate_current()
        .to_string())
}

/// An `otpauth://` URI for a base32 `secret`.
pub fn to_uri(
    params: TotpParams,
    secret: &str,
    issuer: Option<&str>,
    account_name: &str,
) -> Result<String, TotpError> {
    build(params, secret, issuer, account_name)?
        .to_url()
        .map_err(TotpError::Invalid)
}

pub fn parse_uri(uri: &str) -> Result<TotpInfo, TotpError> {
    let totp = Totp::from_url_unchecked(uri).map_err(TotpError::Invalid)?;
    validate(&totp)?;

    Ok(TotpInfo {
        secret: totp.secret().to_base32(),
        issuer: totp.issuer().map(String::from),
        account_name: totp.account_name().to_string(),
        params: TotpParams {
            algorithm: totp.algorithm(),
            digits: totp.digits(),
            period: totp.step(),
        },
    })
}

pub(crate) fn is_valid_totp_secret(s: &str) -> bool {
    base32::decode(base32::Alphabet::Rfc4648 { padding: false }, s).is_some()
}

fn build(
    params: TotpParams,
    secret: &str,
    issuer: Option<&str>,
    account_name: &str,
) -> Result<Totp, TotpError> {
    let secret = Secret::try_from_base32(secret).map_err(TotpError::Secret)?;
    let totp = Builder::new()
        .with_algorithm(params.algorithm)
        .with_digits(params.digits)
        .with_skew(1)
        .with_step_duration(params.period)
        .with_secret(secret)
        .with_issuer(issuer)
        .with_account_name(account_name)
        .build_noncompliant();
    validate(&totp)?;

    Ok(totp)
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
        return Err(TotpError::Invalid(totp_rs::TotpError::SecretTooShort {
            bits: 0,
        }));
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
        .map_err(TotpError::Invalid)
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 80 bits, the kind of secret services like GitHub hand out.
    const SHORT_SECRET: &str = "JBSWY3DPEHPK3PXP";
    /// 160 bits, the length RFC 4226 recommends.
    const LONG_SECRET: &str = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    const SHA1_6_30: TotpParams = TotpParams {
        algorithm: Algorithm::SHA1,
        digits: 6,
        period: 30,
    };

    fn uri(secret: &str) -> String {
        format!("otpauth://totp/GitHub:alice?secret={secret}&issuer=GitHub")
    }

    fn url_with_issuer(issuer: &str) -> Result<String, TotpError> {
        to_uri(SHA1_6_30, SHORT_SECRET, Some(issuer), "alice")
    }

    #[test]
    fn generates_code_for_secret_below_128_bits() {
        let code = generate(SHA1_6_30, SHORT_SECRET).unwrap();
        assert_eq!(6, code.len());
    }

    #[test]
    fn parses_uri_regardless_of_secret_length() {
        let short = parse_uri(&uri(SHORT_SECRET)).unwrap();
        let long = parse_uri(&uri(LONG_SECRET)).unwrap();

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
        assert!(generate(SHA1_6_30, "").is_err());
    }

    #[test]
    fn rejects_digits_outside_rfc_range() {
        assert!(
            generate(
                TotpParams {
                    digits: 9,
                    ..SHA1_6_30
                },
                SHORT_SECRET
            )
            .is_err()
        );
    }

    #[test]
    fn rejects_colon_in_issuer() {
        assert!(url_with_issuer("Git:Hub").is_err());
    }

    #[test]
    fn rejects_uri_without_secret() {
        assert!(parse_uri(&uri("")).is_err());
    }

    #[test]
    fn rejects_secret_that_is_not_base32() {
        assert!(generate(SHA1_6_30, "not base32!").is_err());
    }

    #[test]
    fn rejects_step_of_zero() {
        assert!(
            generate(
                TotpParams {
                    period: 0,
                    ..SHA1_6_30
                },
                SHORT_SECRET
            )
            .is_err()
        );
    }

    /// A step of zero used to reach `generate`, where dividing the timestamp by
    /// it panicked. Anyone who can hand the app a URI picks the period, so this
    /// has to fail as an error rather than take the process down.
    #[test]
    fn rejects_uri_with_period_of_zero() {
        let uri = format!("otpauth://totp/GitHub:alice?secret={SHORT_SECRET}&period=0");

        assert!(parse_uri(&uri).is_err());
    }

    /// totp-rs 6.0 made an empty account name an error rather than emitting
    /// `otpauth://totp/?secret=...` the way 5.x did. The label is required by
    /// the otpauth format, so the caller has to supply one.
    #[test]
    fn requires_account_name_for_url() {
        let url = to_uri(SHA1_6_30, SHORT_SECRET, Some("GitHub"), "");

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
            let params = TotpParams {
                algorithm,
                digits: 8,
                period: 30,
            };
            let totp = build(params, secret, None, "alice").unwrap();

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
