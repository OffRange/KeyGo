use serde::{Deserialize, Serialize};
use thiserror::Error;
use unicode_properties::{GeneralCategory, UnicodeGeneralCategory};
use uuid::Uuid;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Platform {
    Android,
}

impl Platform {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Android => "android",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct DeviceId(Uuid);

impl DeviceId {
    pub fn random() -> Self {
        Self(Uuid::new_v4())
    }

    pub const fn as_uuid(self) -> Uuid {
        self.0
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Error)]
pub enum DeviceNameError {
    #[error("device name must not be blank")]
    Blank,
    #[error("device name must be at most {} characters", DeviceName::MAX_LEN)]
    TooLong,
    #[error("device name must not contain {0:?}")]
    InvalidCharacter(char),
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(try_from = "String")]
pub struct DeviceName(String);

impl DeviceName {
    pub const MAX_LEN: usize = 64;

    pub fn parse(raw: impl AsRef<str>) -> Result<Self, DeviceNameError> {
        let name = raw.as_ref().trim();
        if name.is_empty() {
            return Err(DeviceNameError::Blank);
        }
        if name.chars().count() > Self::MAX_LEN {
            return Err(DeviceNameError::TooLong);
        }
        if let Some(c) = name.chars().find(|&c| is_hidden(c)) {
            return Err(DeviceNameError::InvalidCharacter(c));
        }
        Ok(Self(name.to_owned()))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// Control characters and invisible formatting ones (bidi overrides, zero-width characters, line
/// separators): in the devices list they would let one name pose as another.
fn is_hidden(c: char) -> bool {
    matches!(
        c.general_category(),
        GeneralCategory::Control
            | GeneralCategory::Format
            | GeneralCategory::LineSeparator
            | GeneralCategory::ParagraphSeparator
    )
}

impl TryFrom<String> for DeviceName {
    type Error = DeviceNameError;
    fn try_from(value: String) -> Result<Self, Self::Error> {
        Self::parse(value)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Error)]
pub enum AppVersionError {
    #[error("app version must be 1 to {} characters", AppVersion::MAX_LEN)]
    InvalidLength,
    #[error("app version must not contain {0:?}")]
    InvalidCharacter(char),
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(try_from = "String")]
pub struct AppVersion(String);

impl AppVersion {
    pub const MAX_LEN: usize = 32;

    pub fn parse(raw: impl AsRef<str>) -> Result<Self, AppVersionError> {
        let version = raw.as_ref();
        if version.is_empty() || version.len() > Self::MAX_LEN {
            return Err(AppVersionError::InvalidLength);
        }
        if let Some(c) = version
            .chars()
            .find(|&c| !c.is_ascii_alphanumeric() && !matches!(c, '.' | '+' | '-'))
        {
            return Err(AppVersionError::InvalidCharacter(c));
        }
        Ok(Self(version.to_owned()))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl TryFrom<String> for AppVersion {
    type Error = AppVersionError;
    fn try_from(value: String) -> Result<Self, Self::Error> {
        Self::parse(value)
    }
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DeviceInfo {
    pub name: DeviceName,
    pub platform: Platform,
    pub app_version: AppVersion,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct Device {
    #[serde(rename = "device_id", skip_serializing_if = "Option::is_none")]
    pub id: Option<DeviceId>,
    #[serde(flatten)]
    pub info: DeviceInfo,
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn device_name_is_trimmed() {
        assert_eq!(
            DeviceName::parse("  Pixel 9\t").unwrap().as_str(),
            "Pixel 9"
        );
    }

    #[test]
    fn device_name_accepts_any_visible_text() {
        for raw in ["Ada's Pixel", "Pixel 9 Pro (Arbeit)", "Телефон", "手机 📱"] {
            assert_eq!(DeviceName::parse(raw).unwrap().as_str(), raw);
        }
    }

    #[test]
    fn device_name_enforces_length_bounds() {
        let max = DeviceName::MAX_LEN;
        assert!(DeviceName::parse("é".repeat(max)).is_ok());
        assert_eq!(DeviceName::parse(""), Err(DeviceNameError::Blank));
        assert_eq!(DeviceName::parse(" \n "), Err(DeviceNameError::Blank));
        assert_eq!(
            DeviceName::parse("a".repeat(max + 1)),
            Err(DeviceNameError::TooLong),
        );
    }

    #[test]
    fn device_name_rejects_hidden_characters() {
        for c in [
            '\n',
            '\0',
            '\u{7F}',
            '\u{200B}',
            '\u{202E}',
            '\u{2066}',
            '\u{FEFF}',
            '\u{2029}',
            '\u{E0041}',
        ] {
            assert_eq!(
                DeviceName::parse(format!("Pixel{c}9")),
                Err(DeviceNameError::InvalidCharacter(c)),
            );
        }
    }

    #[test]
    fn app_version_accepts_semver() {
        for raw in ["2.3.0", "2.3.0-beta.1+42", "v2"] {
            assert_eq!(AppVersion::parse(raw).unwrap().as_str(), raw);
        }
    }

    #[test]
    fn app_version_enforces_length_bounds() {
        let max = AppVersion::MAX_LEN;
        assert!(AppVersion::parse("1".repeat(max)).is_ok());
        assert_eq!(AppVersion::parse(""), Err(AppVersionError::InvalidLength));
        assert_eq!(
            AppVersion::parse("1".repeat(max + 1)),
            Err(AppVersionError::InvalidLength),
        );
    }

    #[test]
    fn app_version_rejects_other_characters() {
        for c in [' ', '\n', '/', 'é'] {
            assert_eq!(
                AppVersion::parse(format!("2.3{c}0")),
                Err(AppVersionError::InvalidCharacter(c)),
            );
        }
    }

    #[test]
    fn device_info_validates_while_deserializing() {
        let bad_name =
            json!({ "name": "\u{202E}9 lexiP", "platform": "android", "app_version": "2.3.0" });
        let bad_platform = json!({ "name": "Pixel 9", "platform": "ios", "app_version": "2.3.0" });

        assert!(serde_json::from_value::<DeviceInfo>(bad_name).is_err());
        assert!(serde_json::from_value::<DeviceInfo>(bad_platform).is_err());
    }
}
