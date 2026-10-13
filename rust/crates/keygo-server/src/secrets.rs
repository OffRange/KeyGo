use anyhow::{Context, anyhow, ensure};
use secrecy::{ExposeSecret, SecretString};
use std::fs::{self, DirBuilder, File, Permissions};
use std::io::{ErrorKind, Write};
use std::os::unix::fs::{DirBuilderExt, PermissionsExt};
use std::path::PathBuf;
use zeroize::Zeroizing;

#[derive(Clone, Copy, Debug)]
pub enum Secret {
    DbPassword,
}

impl Secret {
    pub const ALL: &'static [Secret] = &[Secret::DbPassword];

    pub fn name(self) -> &'static str {
        match self {
            Secret::DbPassword => "db_password",
        }
    }

    /// Strength of the values `init` generates.
    pub fn generated_bits(self) -> usize {
        match self {
            Secret::DbPassword => 256,
        }
    }

    pub fn generate(self) -> anyhow::Result<SecretString> {
        let mut raw = Zeroizing::new(vec![0u8; self.generated_bits() / 8]);
        getrandom::fill(raw.as_mut_slice()).map_err(|e| anyhow!("getrandom: {}", e))?;
        Ok(hex::encode(raw.as_slice()).into())
    }

    /// Rejects values the server cannot use at all.
    pub fn validate(self, value: &str) -> anyhow::Result<()> {
        match self {
            Secret::DbPassword => ensure!(!value.is_empty(), "empty"),
        }
        Ok(())
    }

    /// Flags a usable value that looks weak.
    pub fn weakness(self, value: &str) -> Option<String> {
        const MIN_CHARS: usize = 16;
        match self {
            Secret::DbPassword => (value.chars().count() < MIN_CHARS)
                .then(|| format!("shorter than {MIN_CHARS} characters")),
        }
    }
}

#[derive(Debug, PartialEq, Eq)]
pub enum CreateOutcome {
    Created,
    AlreadyExists,
}

pub struct SecretStore {
    dir: PathBuf,
}

impl SecretStore {
    pub fn new(dir: impl Into<PathBuf>) -> Self {
        Self { dir: dir.into() }
    }

    pub fn path(&self, secret: Secret) -> PathBuf {
        self.dir.join(secret.name())
    }

    pub fn create_dir(&self) -> anyhow::Result<()> {
        DirBuilder::new()
            .recursive(true)
            .mode(0o700)
            .create(&self.dir)
            .with_context(|| format!("cannot create {}", self.dir.display()))
    }

    pub fn load(&self, secret: Secret) -> anyhow::Result<SecretString> {
        let path = self.path(secret);
        let bytes = Zeroizing::new(
            fs::read(&path).with_context(|| format!("cannot read {}", path.display()))?,
        );
        let value = str::from_utf8(&bytes)
            .with_context(|| format!("{} is not UTF-8", path.display()))?
            .trim();
        secret
            .validate(value)
            .with_context(|| format!("{} is invalid", path.display()))?;
        Ok(value.into())
    }

    pub fn create(&self, secret: Secret, value: &SecretString) -> anyhow::Result<CreateOutcome> {
        secret
            .validate(value.expose_secret())
            .with_context(|| format!("refusing to store invalid {}", secret.name()))?;

        let path = self.path(secret);
        let mut tmp = tempfile::Builder::new()
            .prefix(&format!(".{}.", secret.name()))
            .permissions(Permissions::from_mode(0o400))
            .tempfile_in(&self.dir)
            .with_context(|| format!("cannot create a temp file in {}", self.dir.display()))?;
        tmp.write_all(value.expose_secret().as_bytes())?;
        tmp.as_file().sync_all()?;

        match tmp.persist_noclobber(&path) {
            Ok(_) => {}
            Err(e) if e.error.kind() == ErrorKind::AlreadyExists => {
                return Ok(CreateOutcome::AlreadyExists);
            }
            Err(e) => {
                return Err(e.error).with_context(|| format!("cannot create {}", path.display()));
            }
        }

        File::open(&self.dir)
            .and_then(|dir| dir.sync_all())
            .with_context(|| format!("cannot sync {}", self.dir.display()))?;
        Ok(CreateOutcome::Created)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::os::unix::fs::MetadataExt;

    const VALID: &str = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    fn store() -> (tempfile::TempDir, SecretStore) {
        let dir = tempfile::tempdir().unwrap();
        let store = SecretStore::new(dir.path());
        (dir, store)
    }

    fn write_raw(store: &SecretStore, secret: Secret, contents: &[u8]) {
        fs::write(store.path(secret), contents).unwrap();
    }

    #[test]
    fn generated_db_password_is_valid() {
        let value = Secret::DbPassword.generate().unwrap();

        assert!(Secret::DbPassword.validate(value.expose_secret()).is_ok());
    }

    #[test]
    fn generated_values_differ() {
        let a = Secret::DbPassword.generate().unwrap();
        let b = Secret::DbPassword.generate().unwrap();

        assert_ne!(a.expose_secret(), b.expose_secret());
    }

    #[test]
    fn generated_value_has_exactly_the_generated_bits() {
        let value = Secret::DbPassword.generate().unwrap();

        assert_eq!(
            value.expose_secret().len() * 4,
            Secret::DbPassword.generated_bits()
        );
        assert_eq!(Secret::DbPassword.weakness(value.expose_secret()), None);
    }

    #[test]
    fn externally_issued_password_is_accepted() {
        assert!(Secret::DbPassword.validate("Tr0ub4dor&3-Cloud!").is_ok());
    }

    #[test]
    fn empty_password_is_rejected() {
        assert!(Secret::DbPassword.validate("").is_err());
    }

    #[test]
    fn short_password_is_usable_but_weak() {
        assert!(Secret::DbPassword.validate("hunter2").is_ok());
        assert_eq!(
            Secret::DbPassword.weakness("hunter2").as_deref(),
            Some("shorter than 16 characters"),
        );
        assert_eq!(Secret::DbPassword.weakness(&"x".repeat(16)), None);
    }

    #[test]
    fn create_then_load_round_trips() {
        let (_dir, store) = store();

        let outcome = store.create(Secret::DbPassword, &VALID.into()).unwrap();

        assert_eq!(outcome, CreateOutcome::Created);
        assert_eq!(
            store.load(Secret::DbPassword).unwrap().expose_secret(),
            VALID
        );
    }

    #[test]
    fn create_writes_owner_read_only_file() {
        let (_dir, store) = store();

        store.create(Secret::DbPassword, &VALID.into()).unwrap();

        let mode = fs::metadata(store.path(Secret::DbPassword)).unwrap().mode();
        assert_eq!(mode & 0o777, 0o400);
    }

    #[test]
    fn create_leaves_no_temp_files_behind() {
        let (dir, store) = store();

        store.create(Secret::DbPassword, &VALID.into()).unwrap();

        let names: Vec<_> = fs::read_dir(dir.path())
            .unwrap()
            .map(|e| e.unwrap().file_name())
            .collect();
        assert_eq!(names, ["db_password"]);
    }

    #[test]
    fn create_does_not_overwrite_existing_secret() {
        let (_dir, store) = store();
        let existing = "f".repeat(64);
        write_raw(&store, Secret::DbPassword, existing.as_bytes());

        let outcome = store.create(Secret::DbPassword, &VALID.into()).unwrap();

        assert_eq!(outcome, CreateOutcome::AlreadyExists);
        assert_eq!(
            fs::read_to_string(store.path(Secret::DbPassword)).unwrap(),
            existing
        );
    }

    #[test]
    fn create_rejects_invalid_value_without_writing() {
        let (dir, store) = store();

        assert!(store.create(Secret::DbPassword, &"".into()).is_err());
        assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 0);
    }

    #[test]
    fn load_strips_trailing_line_breaks() {
        let (_dir, store) = store();
        write_raw(
            &store,
            Secret::DbPassword,
            format!("{VALID}\r\n").as_bytes(),
        );

        let value = store.load(Secret::DbPassword).unwrap();

        assert_eq!(value.expose_secret(), VALID);
    }

    #[test]
    fn load_fails_when_missing() {
        let (_dir, store) = store();

        let err = store.load(Secret::DbPassword).unwrap_err();

        assert_eq!(
            err.downcast_ref::<std::io::Error>().map(|e| e.kind()),
            Some(ErrorKind::NotFound),
        );
    }

    #[test]
    fn load_rejects_empty_value() {
        let (_dir, store) = store();
        write_raw(&store, Secret::DbPassword, b"\r\n");

        assert!(store.load(Secret::DbPassword).is_err());
    }

    #[test]
    fn load_rejects_non_utf8() {
        let (_dir, store) = store();
        write_raw(&store, Secret::DbPassword, &[0xff; 64]);

        assert!(store.load(Secret::DbPassword).is_err());
    }

    #[test]
    fn create_dir_makes_owner_only_directory() {
        let (dir, _) = store();
        let nested = dir.path().join("nested/secrets");

        SecretStore::new(&nested).create_dir().unwrap();

        let mode = fs::metadata(&nested).unwrap().mode();
        assert_eq!(mode & 0o777, 0o700);
    }
}
