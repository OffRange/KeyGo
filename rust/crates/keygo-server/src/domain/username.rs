use serde::{Deserialize, Serialize};
use std::fmt;
use thiserror::Error;
use unicode_normalization::UnicodeNormalization;

const SEPARATORS: [char; 3] = ['.', '-', '_'];

#[derive(Debug, Clone, Copy, PartialEq, Eq, Error)]
pub enum UsernameError {
    #[error("username must be at least {} characters", Username::MIN_LEN)]
    TooShort,
    #[error("username must be at most {} characters", Username::MAX_LEN)]
    TooLong,
    #[error(
        "username must not contain {0:?}. Valid chars are {valid}",
        valid = Username::ALLOWED_CHARS.join(", ")
    )]
    InvalidCharacter(char),
    #[error("username must start with a letter or digit")]
    InvalidStart,
    #[error("username must end with a letter or digit")]
    InvalidEnd,
    #[error("username must not contain consecutive '.', '_' or '-'")]
    ConsecutiveSeparators,
    #[error("username must not consist of digits only")]
    OnlyDigits,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(try_from = "String")]
pub struct Username(String);

impl Username {
    pub const MIN_LEN: usize = 3;
    pub const MAX_LEN: usize = 32;
    pub const ALLOWED_CHARS: [&str; 5] = ["a-z", "0-9", ".", "_", "-"];

    pub fn parse(raw: impl AsRef<str>) -> Result<Self, UsernameError> {
        let raw = raw.as_ref();
        if raw.chars().count() > Self::MAX_LEN {
            return Err(UsernameError::TooLong);
        }

        let name: String = raw.nfkc().map(|c| c.to_ascii_lowercase()).collect();
        validate(&name)?;
        Ok(Self(name))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

fn validate(name: &str) -> Result<(), UsernameError> {
    if let Some(c) = name
        .chars()
        .find(|&c| !is_alphanumeric(c) && !is_separator(c))
    {
        return Err(UsernameError::InvalidCharacter(c));
    }

    let error = if name.len() < Username::MIN_LEN {
        UsernameError::TooShort
    } else if name.len() > Username::MAX_LEN {
        // NFKC may expand a character (`ﬁ` -> `fi`), so the pre-normalization bound is not enough.
        UsernameError::TooLong
    } else if name.starts_with(is_separator) {
        UsernameError::InvalidStart
    } else if name.ends_with(is_separator) {
        UsernameError::InvalidEnd
    } else if name.split(is_separator).any(str::is_empty) {
        // With both ends alphanumeric, an empty segment can only come from adjacent separators.
        UsernameError::ConsecutiveSeparators
    } else if name.chars().all(|c| c.is_ascii_digit()) {
        UsernameError::OnlyDigits
    } else {
        return Ok(());
    };
    Err(error)
}

fn is_alphanumeric(c: char) -> bool {
    c.is_ascii_lowercase() || c.is_ascii_digit()
}

fn is_separator(c: char) -> bool {
    SEPARATORS.contains(&c)
}

impl fmt::Display for Username {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl AsRef<str> for Username {
    fn as_ref(&self) -> &str {
        self.as_str()
    }
}

impl TryFrom<String> for Username {
    type Error = UsernameError;
    fn try_from(value: String) -> Result<Self, Self::Error> {
        Username::parse(value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parsed(raw: &str) -> String {
        Username::parse(raw).unwrap().as_ref().to_owned()
    }

    fn rejected(raw: &str) -> UsernameError {
        Username::parse(raw).unwrap_err()
    }

    #[test]
    fn lowercases_ascii() {
        assert_eq!(parsed("John.Doe"), "john.doe");
    }

    #[test]
    fn folds_fullwidth_and_compatibility_forms() {
        assert_eq!(parsed("ｊｏｈｎ"), "john");
        assert_eq!(parsed("ﬁnn"), "finn");
    }

    #[test]
    fn accepts_single_separators_between_alphanumerics() {
        for raw in ["j.o_h-n", "a-1", "0xff", "user_42"] {
            assert_eq!(parsed(raw), raw);
        }
    }

    #[test]
    fn enforces_length_bounds() {
        assert_eq!(parsed("abc"), "abc");
        let max = Username::MAX_LEN;
        assert_eq!(parsed(&"a".repeat(max)), "a".repeat(max));
        assert_eq!(rejected(""), UsernameError::TooShort);
        assert_eq!(rejected("ab"), UsernameError::TooShort);
        assert_eq!(rejected(&"a".repeat(max + 1)), UsernameError::TooLong);
    }

    #[test]
    fn length_bound_admits_longest_fullwidth_name() {
        // Fullwidth letters are three UTF-8 bytes each; the pre-normalization bound must not
        // reject a name that normalizes to exactly MAX_LEN.
        let raw = "ａ".repeat(Username::MAX_LEN);
        assert_eq!(parsed(&raw), "a".repeat(Username::MAX_LEN));
    }

    #[test]
    fn rejects_oversized_input_before_normalizing() {
        assert_eq!(rejected(&"ａ".repeat(1_000)), UsernameError::TooLong);
    }

    #[test]
    fn rejects_characters_outside_the_alphabet() {
        assert_eq!(rejected("jo hn"), UsernameError::InvalidCharacter(' '));
        assert_eq!(rejected("jöhn"), UsernameError::InvalidCharacter('ö'));
        assert_eq!(rejected("jo@hn"), UsernameError::InvalidCharacter('@'));
        assert_eq!(rejected("john\u{0}"), UsernameError::InvalidCharacter('\0'));
    }

    #[test]
    fn reports_invalid_character_before_length() {
        assert_eq!(rejected("ö"), UsernameError::InvalidCharacter('ö'));
    }

    #[test]
    fn requires_alphanumeric_ends() {
        assert_eq!(rejected(".john"), UsernameError::InvalidStart);
        assert_eq!(rejected("_john"), UsernameError::InvalidStart);
        assert_eq!(rejected("john-"), UsernameError::InvalidEnd);
        assert_eq!(rejected("john."), UsernameError::InvalidEnd);
    }

    #[test]
    fn rejects_consecutive_separators() {
        assert_eq!(rejected("jo..hn"), UsernameError::ConsecutiveSeparators);
        assert_eq!(rejected("jo._hn"), UsernameError::ConsecutiveSeparators);
    }

    #[test]
    fn rejects_digit_only_names() {
        assert_eq!(rejected("123"), UsernameError::OnlyDigits);
        assert_eq!(parsed("123a"), "123a");
        assert_eq!(parsed("1.2.3a"), "1.2.3a");
    }

    #[test]
    fn allowed_chars_match_validation() {
        let advertised = |c: char| {
            Username::ALLOWED_CHARS
                .iter()
                .any(|entry| match entry.as_bytes() {
                    [single] => c == char::from(*single),
                    [from, b'-', to] => (char::from(*from)..=char::from(*to)).contains(&c),
                    _ => panic!("unexpected ALLOWED_CHARS entry {entry:?}"),
                })
        };

        for c in (0..=0x7f).map(char::from) {
            let accepted = is_alphanumeric(c) || is_separator(c);
            assert_eq!(advertised(c), accepted, "{c:?}");
        }
    }
}
