use std::sync::Arc;

use lib::backup as core;
use lib::crypto::AccountRootKey;

#[derive(uniffi::Record)]
pub struct Backup {
    pub vaults: Vec<BackupVault>,
}

#[derive(uniffi::Record)]
pub struct BackupVault {
    pub name: String,
    pub icon: String,
    pub logins: Vec<BackupLogin>,
    pub cards: Vec<BackupCard>,
}

#[derive(uniffi::Record)]
pub struct BackupLogin {
    pub title: String,
    pub notes: Option<String>,
    pub tags: Vec<String>,
    pub pinned: bool,
    pub username: Option<String>,
    pub password: Option<String>,
    pub totp_secret: Option<String>,
    pub website: Option<String>,
    pub passkeys: Vec<BackupPasskey>,
}

#[derive(uniffi::Record)]
pub struct BackupCard {
    pub title: String,
    pub notes: Option<String>,
    pub tags: Vec<String>,
    pub pinned: bool,
    pub cardholder: Option<String>,
    pub number: String,
    pub expiration_month: Option<u8>,
    pub expiration_year: Option<u16>,
    pub cvv: Option<String>,
}

#[derive(uniffi::Record)]
pub struct BackupPasskey {
    pub user_name: String,
    pub user_display_name: String,
    pub credential_id: Vec<u8>,
    pub private_key: Vec<u8>,
    pub rp: String,
}

impl From<core::Passkey> for BackupPasskey {
    fn from(p: core::Passkey) -> Self {
        Self {
            user_name: p.user_name,
            user_display_name: p.user_display_name,
            credential_id: p.credential_id,
            private_key: p.private_key,
            rp: p.rp,
        }
    }
}

impl From<BackupPasskey> for core::Passkey {
    fn from(p: BackupPasskey) -> Self {
        Self {
            user_name: p.user_name,
            user_display_name: p.user_display_name,
            credential_id: p.credential_id,
            private_key: p.private_key,
            rp: p.rp,
        }
    }
}

impl From<core::Login> for BackupLogin {
    fn from(l: core::Login) -> Self {
        Self {
            title: l.title,
            notes: l.notes,
            tags: l.tags,
            pinned: l.pinned,
            username: l.username,
            password: l.password,
            totp_secret: l.totp_secret,
            website: l.website,
            passkeys: l.passkeys.into_iter().map(Into::into).collect(),
        }
    }
}

impl From<BackupLogin> for core::Login {
    fn from(l: BackupLogin) -> Self {
        Self {
            title: l.title,
            notes: l.notes,
            tags: l.tags,
            pinned: l.pinned,
            username: l.username,
            password: l.password,
            totp_secret: l.totp_secret,
            website: l.website,
            passkeys: l.passkeys.into_iter().map(Into::into).collect(),
        }
    }
}

impl From<core::Card> for BackupCard {
    fn from(c: core::Card) -> Self {
        Self {
            title: c.title,
            notes: c.notes,
            tags: c.tags,
            pinned: c.pinned,
            cardholder: c.cardholder,
            number: c.number,
            expiration_month: c.expiration_month,
            expiration_year: c.expiration_year,
            cvv: c.cvv,
        }
    }
}

impl From<BackupCard> for core::Card {
    fn from(c: BackupCard) -> Self {
        Self {
            title: c.title,
            notes: c.notes,
            tags: c.tags,
            pinned: c.pinned,
            cardholder: c.cardholder,
            number: c.number,
            expiration_month: c.expiration_month,
            expiration_year: c.expiration_year,
            cvv: c.cvv,
        }
    }
}

impl From<core::Vault> for BackupVault {
    fn from(v: core::Vault) -> Self {
        Self {
            name: v.name,
            icon: v.icon,
            logins: v.logins.into_iter().map(Into::into).collect(),
            cards: v.cards.into_iter().map(Into::into).collect(),
        }
    }
}

impl From<BackupVault> for core::Vault {
    fn from(v: BackupVault) -> Self {
        Self {
            name: v.name,
            icon: v.icon,
            logins: v.logins.into_iter().map(Into::into).collect(),
            cards: v.cards.into_iter().map(Into::into).collect(),
        }
    }
}

impl From<core::Backup> for Backup {
    fn from(b: core::Backup) -> Self {
        Self {
            vaults: b.vaults.into_iter().map(Into::into).collect(),
        }
    }
}

impl From<Backup> for core::Backup {
    fn from(b: Backup) -> Self {
        Self {
            vaults: b.vaults.into_iter().map(Into::into).collect(),
        }
    }
}

#[derive(uniffi::Record)]
pub struct CsvColumn {
    pub index: u32,
    pub header: String,
    pub sample_values: Vec<String>,
}

#[derive(uniffi::Enum)]
pub enum Confidence {
    High,
    Medium,
    Low,
}

#[derive(uniffi::Record)]
pub struct FieldConfidence {
    pub title: Option<Confidence>,
    pub url: Option<Confidence>,
    pub username: Option<Confidence>,
    pub password: Option<Confidence>,
    pub notes: Option<Confidence>,
    pub totp: Option<Confidence>,
}

#[derive(uniffi::Record, Default)]
pub struct ColumnMapping {
    pub title: Option<u32>,
    pub url: Option<u32>,
    pub username: Option<u32>,
    pub password: Option<u32>,
    pub notes: Option<u32>,
    pub totp: Option<u32>,
}

#[derive(uniffi::Record)]
pub struct CsvAnalysis {
    pub columns: Vec<CsvColumn>,
    pub suggested: ColumnMapping,
    pub confidence: FieldConfidence,
}

#[derive(uniffi::Record)]
pub struct ImportReport {
    pub imported: u32,
    pub skipped: u32,
}

#[derive(uniffi::Record)]
pub struct CsvImportResult {
    pub backup: Backup,
    pub report: ImportReport,
}

#[derive(uniffi::Enum)]
pub enum ExportPreset {
    KeyGo,
    Browser,
}

#[derive(uniffi::Enum)]
pub enum JsonEncryption {
    None,
    Passphrase,
    Ark,
}

impl From<core::Confidence> for Confidence {
    fn from(c: core::Confidence) -> Self {
        match c {
            core::Confidence::High => Self::High,
            core::Confidence::Medium => Self::Medium,
            core::Confidence::Low => Self::Low,
        }
    }
}

impl From<core::CsvColumn> for CsvColumn {
    fn from(c: core::CsvColumn) -> Self {
        Self {
            index: c.index,
            header: c.header,
            sample_values: c.sample_values,
        }
    }
}

impl From<core::FieldConfidence> for FieldConfidence {
    fn from(f: core::FieldConfidence) -> Self {
        Self {
            title: f.title.map(Into::into),
            url: f.url.map(Into::into),
            username: f.username.map(Into::into),
            password: f.password.map(Into::into),
            notes: f.notes.map(Into::into),
            totp: f.totp.map(Into::into),
        }
    }
}

impl From<core::ColumnMapping> for ColumnMapping {
    fn from(m: core::ColumnMapping) -> Self {
        Self {
            title: m.title.map(|i| i as u32),
            url: m.url.map(|i| i as u32),
            username: m.username.map(|i| i as u32),
            password: m.password.map(|i| i as u32),
            notes: m.notes.map(|i| i as u32),
            totp: m.totp.map(|i| i as u32),
        }
    }
}

impl From<ColumnMapping> for core::ColumnMapping {
    fn from(m: ColumnMapping) -> Self {
        Self {
            title: m.title.map(|i| i as usize),
            url: m.url.map(|i| i as usize),
            username: m.username.map(|i| i as usize),
            password: m.password.map(|i| i as usize),
            notes: m.notes.map(|i| i as usize),
            totp: m.totp.map(|i| i as usize),
        }
    }
}

impl From<core::CsvAnalysis> for CsvAnalysis {
    fn from(a: core::CsvAnalysis) -> Self {
        Self {
            columns: a.columns.into_iter().map(Into::into).collect(),
            suggested: a.suggested.into(),
            confidence: a.confidence.into(),
        }
    }
}

impl From<core::ImportReport> for ImportReport {
    fn from(r: core::ImportReport) -> Self {
        Self {
            imported: r.imported,
            skipped: r.skipped,
        }
    }
}

impl From<ExportPreset> for core::ExportPreset {
    fn from(p: ExportPreset) -> Self {
        match p {
            ExportPreset::KeyGo => core::ExportPreset::KeyGo,
            ExportPreset::Browser => core::ExportPreset::Browser,
        }
    }
}

#[derive(uniffi::Enum)]
pub enum BackupCredential {
    Passphrase { bytes: Vec<u8> },
    Ark { key: AccountRootKey },
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum BackupError {
    #[error("crypto error: {0}")]
    Crypto(String),
    #[error("json error: {0}")]
    Json(String),
    #[error("invalid base64 in backup payload or header")]
    Base64,
    #[error("unsupported backup version: {0}")]
    UnsupportedVersion(u32),
    #[error("malformed encryption header")]
    MalformedHeader,
    #[error("encryption header and payload disagree")]
    EncryptionMismatch,
    #[error("a credential is required to read this encrypted backup")]
    MissingCredential,
    #[error("a credential was supplied for a plaintext backup")]
    UnexpectedCredential,
    #[error("credential does not match the backup's key source")]
    CredentialMismatch,
    #[error("malformed csv: {0}")]
    Csv(String),
    #[error("csv contained no rows")]
    EmptyCsv,
}

impl From<core::BackupError> for BackupError {
    fn from(e: core::BackupError) -> Self {
        use core::BackupError as E;
        match e {
            E::Crypto(c) => Self::Crypto(format!("{c}")),
            E::Json(j) => Self::Json(format!("{j}")),
            E::Base64 => Self::Base64,
            E::UnsupportedVersion(v) => Self::UnsupportedVersion(v),
            E::MalformedHeader => Self::MalformedHeader,
            E::EncryptionMismatch => Self::EncryptionMismatch,
            E::MissingCredential => Self::MissingCredential,
            E::UnexpectedCredential => Self::UnexpectedCredential,
            E::CredentialMismatch => Self::CredentialMismatch,
            E::Csv(s) => Self::Csv(s),
            E::EmptyCsv => Self::EmptyCsv,
        }
    }
}

#[derive(uniffi::Object)]
pub struct JsonBackupManager;

#[uniffi::export]
impl JsonBackupManager {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn export(
        &self,
        backup: Backup,
        credential: Option<BackupCredential>,
    ) -> Result<String, BackupError> {
        let backup: core::Backup = backup.into();
        let json = match credential {
            None => core::json::export(&backup, None),
            Some(BackupCredential::Passphrase { bytes }) => {
                core::json::export(&backup, Some(core::BackupCredential::Passphrase(&bytes)))
            }
            Some(BackupCredential::Ark { key }) => {
                core::json::export(&backup, Some(core::BackupCredential::Ark(&key)))
            }
        }?;
        Ok(json)
    }

    pub fn import(
        &self,
        data: String,
        credential: Option<BackupCredential>,
    ) -> Result<Backup, BackupError> {
        let backup = match credential {
            None => core::json::import(&data, None),
            Some(BackupCredential::Passphrase { bytes }) => {
                core::json::import(&data, Some(core::BackupCredential::Passphrase(&bytes)))
            }
            Some(BackupCredential::Ark { key }) => {
                core::json::import(&data, Some(core::BackupCredential::Ark(&key)))
            }
        }?;
        Ok(backup.into())
    }

    pub fn inspect(&self, data: String) -> Result<JsonEncryption, BackupError> {
        Ok(match core::json::inspect(&data)? {
            None => JsonEncryption::None,
            Some(core::encryption::KeySource::Passphrase) => JsonEncryption::Passphrase,
            Some(core::encryption::KeySource::Ark) => JsonEncryption::Ark,
        })
    }
}

#[derive(uniffi::Object)]
pub struct CsvBackupManager;

#[uniffi::export]
impl CsvBackupManager {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn analyze(&self, data: String) -> Result<CsvAnalysis, BackupError> {
        Ok(core::csv::analyze(&data)?.into())
    }

    pub fn import(
        &self,
        data: String,
        mapping: ColumnMapping,
    ) -> Result<CsvImportResult, BackupError> {
        let mapping: core::ColumnMapping = mapping.into();
        let (backup, report) = core::csv::import(&data, &mapping)?;
        Ok(CsvImportResult {
            backup: backup.into(),
            report: report.into(),
        })
    }

    pub fn export(&self, backup: Backup, preset: ExportPreset) -> Result<String, BackupError> {
        let backup: core::Backup = backup.into();
        Ok(core::csv::export(&backup, preset.into())?)
    }
}
