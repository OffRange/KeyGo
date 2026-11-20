use std::sync::Arc;

use keygo_api::domain::{Username, UsernameError as CoreUsernameError};

/// Why a username fails the on-device check. Mirrors the server's validation, plus `Reserved`,
/// which the server reports only as `invalid_username` at registration.
#[derive(Debug, PartialEq, Eq, thiserror::Error, uniffi::Error)]
pub enum UsernameError {
    #[error("username must be at least {min_len} characters")]
    TooShort { min_len: u32 },
    #[error("username must be at most {max_len} characters")]
    TooLong { max_len: u32 },
    #[error("username must not contain {character:?}")]
    InvalidCharacter { character: String },
    #[error("username must start with a letter or digit")]
    InvalidStart,
    #[error("username must end with a letter or digit")]
    InvalidEnd,
    #[error("username must not contain consecutive '.', '_' or '-'")]
    ConsecutiveSeparators,
    #[error("username must not consist of digits only")]
    OnlyDigits,
    #[error("username is reserved")]
    Reserved,
}

impl From<CoreUsernameError> for UsernameError {
    fn from(value: CoreUsernameError) -> Self {
        match value {
            CoreUsernameError::TooShort => Self::TooShort {
                min_len: Username::MIN_LEN as u32,
            },
            CoreUsernameError::TooLong => Self::TooLong {
                max_len: Username::MAX_LEN as u32,
            },
            CoreUsernameError::InvalidCharacter(c) => Self::InvalidCharacter {
                character: c.to_string(),
            },
            CoreUsernameError::InvalidStart => Self::InvalidStart,
            CoreUsernameError::InvalidEnd => Self::InvalidEnd,
            CoreUsernameError::ConsecutiveSeparators => Self::ConsecutiveSeparators,
            CoreUsernameError::OnlyDigits => Self::OnlyDigits,
        }
    }
}

#[derive(uniffi::Object)]
pub struct UsernameValidator;

#[uniffi::export]
impl UsernameValidator {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    /// Returns the normalized name (NFKC, lowercased) the server would store, or why it would
    /// refuse a registration with it.
    pub fn validate(&self, input: String) -> Result<String, UsernameError> {
        let username = Username::parse(input)?;
        if username.is_reserved() {
            return Err(UsernameError::Reserved);
        }
        Ok(username.as_str().to_owned())
    }

    pub fn min_len(&self) -> u32 {
        Username::MIN_LEN as u32
    }

    pub fn max_len(&self) -> u32 {
        Username::MAX_LEN as u32
    }

    /// Display form of the accepted characters: ranges like `a-z` and single characters.
    pub fn allowed_chars(&self) -> Vec<String> {
        Username::ALLOWED_CHARS.map(str::to_owned).to_vec()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn validate(raw: &str) -> Result<String, UsernameError> {
        UsernameValidator.validate(raw.to_owned())
    }

    #[test]
    fn returns_normalized_name() {
        assert_eq!(validate("Ｊｏｈｎ.Doe").unwrap(), "john.doe");
    }

    #[test]
    fn maps_core_errors() {
        assert_eq!(validate("ab"), Err(UsernameError::TooShort { min_len: 3 }));
        assert_eq!(
            validate(&"a".repeat(33)),
            Err(UsernameError::TooLong { max_len: 32 }),
        );
        assert_eq!(
            validate("jöhn"),
            Err(UsernameError::InvalidCharacter {
                character: "ö".to_owned(),
            }),
        );
        assert_eq!(validate(".john"), Err(UsernameError::InvalidStart));
        assert_eq!(validate("john-"), Err(UsernameError::InvalidEnd));
        assert_eq!(
            validate("jo..hn"),
            Err(UsernameError::ConsecutiveSeparators)
        );
        assert_eq!(validate("123"), Err(UsernameError::OnlyDigits));
    }

    #[test]
    fn rejects_reserved_names_after_normalizing() {
        assert_eq!(validate("Admin"), Err(UsernameError::Reserved));
        assert_eq!(validate("ｒｏｏｔ"), Err(UsernameError::Reserved));
        assert_eq!(validate("admin2").unwrap(), "admin2");
    }
}
