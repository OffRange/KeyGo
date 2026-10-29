use crate::domain::{AccessTokenKey, InvitePepper};
use anyhow::{Context, anyhow, ensure};
use keygo_opaque::server::ServerSetup;
use secrecy::{ExposeSecret, SecretString};
use std::fs::{self, DirBuilder, File, Permissions};
use std::io::{ErrorKind, Write};
use std::os::unix::fs::{DirBuilderExt, PermissionsExt};
use std::path::{Path, PathBuf};
use zeroize::Zeroizing;

macro_rules! secrets {
    ($($ty:ident),+ $(,)?) => {
        #[derive(Clone, Copy, Debug)]
        pub enum Secret {
            $($ty,)+
        }

        impl Secret {
            pub const ALL: &'static [Secret] = &[$(Secret::$ty),+];

            pub const fn name(self) -> &'static str {
                match self {
                    $(Secret::$ty => <$ty as TypedSecret>::NAME,)+
                }
            }

            pub fn generate(self) -> anyhow::Result<SecretString> {
                match self {
                    $(Secret::$ty => <$ty as TypedSecret>::generate(),)+
                }
            }

            /// Fails if the server cannot use `stored`, otherwise says why it looks weak, if it does.
            fn assess(self, stored: &str) -> anyhow::Result<Option<String>> {
                match self {
                    $(Secret::$ty => assess::<$ty>(stored),)+
                }
            }
        }
    };
}

fn assess<T: TypedSecret>(stored: &str) -> anyhow::Result<Option<String>> {
    T::decode(stored).map(|value| T::weakness(&value))
}

pub trait TypedSecret {
    /// File name inside the secrets directory.
    const NAME: &'static str;
    type Value;

    fn generate() -> anyhow::Result<SecretString>;

    fn decode(stored: &str) -> anyhow::Result<Self::Value>;
    fn weakness(_value: &Self::Value) -> Option<String> {
        None
    }
}

pub struct DbPassword;

impl DbPassword {
    const GENERATED_BITS: usize = 256;
}

impl TypedSecret for DbPassword {
    const NAME: &'static str = "db_password";
    type Value = SecretString;

    fn generate() -> anyhow::Result<SecretString> {
        let mut raw = Zeroizing::new(vec![0u8; Self::GENERATED_BITS / 8]);
        getrandom::fill(raw.as_mut_slice()).map_err(|e| anyhow!("getrandom: {}", e))?;
        Ok(hex::encode(raw.as_slice()).into())
    }

    fn decode(stored: &str) -> anyhow::Result<SecretString> {
        ensure!(!stored.is_empty(), "empty");
        Ok(stored.into())
    }

    fn weakness(value: &SecretString) -> Option<String> {
        const MIN_CHARS: usize = 16;
        (value.expose_secret().chars().count() < MIN_CHARS)
            .then(|| format!("shorter than {MIN_CHARS} characters"))
    }
}

pub struct OpaqueSetup;

impl TypedSecret for OpaqueSetup {
    const NAME: &'static str = "opaque_setup";
    type Value = ServerSetup;

    fn generate() -> anyhow::Result<SecretString> {
        Ok(hex::encode(ServerSetup::generate().to_bytes().as_slice()).into())
    }

    fn decode(stored: &str) -> anyhow::Result<ServerSetup> {
        let raw = Zeroizing::new(hex::decode(stored).context("not hex")?);
        ServerSetup::from_bytes(&raw).context("not an OPAQUE server setup")
    }
}

impl TypedSecret for InvitePepper {
    const NAME: &'static str = "invite_pepper";
    type Value = Self;

    fn generate() -> anyhow::Result<SecretString> {
        random_key()
    }

    fn decode(stored: &str) -> anyhow::Result<Self> {
        Ok(Self::new(&*decode(stored)?))
    }
}

impl TypedSecret for AccessTokenKey {
    const NAME: &'static str = "access_token_key";
    type Value = Self;

    fn generate() -> anyhow::Result<SecretString> {
        random_key()
    }

    fn decode(stored: &str) -> anyhow::Result<Self::Value> {
        Ok(AccessTokenKey::new(&*decode(stored)?))
    }
}

fn random_key() -> anyhow::Result<SecretString> {
    let mut raw = Zeroizing::new([0u8; 32]);
    getrandom::fill(raw.as_mut_slice()).map_err(|e| anyhow!("getrandom: {}", e))?;
    Ok(hex::encode(raw.as_slice()).into())
}

fn decode(stored: &str) -> anyhow::Result<Zeroizing<[u8; 32]>> {
    let raw = Zeroizing::new(hex::decode(stored).context("not hex")?);
    ensure!(raw.len() == 32, "not 32 bytes");
    let mut bytes = Zeroizing::new([0u8; 32]);
    bytes.copy_from_slice(&raw);
    Ok(bytes)
}

secrets! {
    DbPassword,
    OpaqueSetup,
    InvitePepper,
    AccessTokenKey,
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

    fn path_of<T: TypedSecret>(&self) -> PathBuf {
        self.dir.join(T::NAME)
    }

    pub fn create_dir(&self) -> anyhow::Result<()> {
        DirBuilder::new()
            .recursive(true)
            .mode(0o700)
            .create(&self.dir)
            .with_context(|| format!("cannot create {}", self.dir.display()))
    }

    pub fn read<T: TypedSecret>(&self) -> anyhow::Result<T::Value> {
        let path = self.path_of::<T>();
        let stored = read_text(&path)?;
        T::decode(stored.expose_secret()).with_context(|| format!("{} is invalid", path.display()))
    }

    pub fn inspect(&self, secret: Secret) -> anyhow::Result<Option<String>> {
        let path = self.path(secret);
        let stored = read_text(&path)?;
        secret
            .assess(stored.expose_secret())
            .with_context(|| format!("{} is invalid", path.display()))
    }

    pub fn create(&self, secret: Secret, value: &SecretString) -> anyhow::Result<CreateOutcome> {
        secret
            .assess(value.expose_secret())
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

fn read_text(path: &Path) -> anyhow::Result<SecretString> {
    let bytes =
        Zeroizing::new(fs::read(path).with_context(|| format!("cannot read {}", path.display()))?);
    let value = str::from_utf8(&bytes)
        .with_context(|| format!("{} is not UTF-8", path.display()))?
        .trim();
    Ok(value.into())
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

        assert!(DbPassword::decode(value.expose_secret()).is_ok());
    }

    #[test]
    fn generated_values_differ() {
        let a = Secret::DbPassword.generate().unwrap();
        let b = Secret::DbPassword.generate().unwrap();

        assert_ne!(a.expose_secret(), b.expose_secret());
    }

    #[test]
    fn externally_issued_password_is_accepted() {
        assert!(DbPassword::decode("Tr0ub4dor&3-Cloud!").is_ok());
    }

    #[test]
    fn empty_password_is_rejected() {
        assert!(DbPassword::decode("").is_err());
    }

    #[test]
    fn short_password_is_usable_but_weak() {
        assert_eq!(
            Secret::DbPassword.assess("hunter2").unwrap().as_deref(),
            Some("shorter than 16 characters"),
        );
        assert_eq!(Secret::DbPassword.assess(&"x".repeat(16)).unwrap(), None);
    }

    #[test]
    fn generated_opaque_setup_is_valid_and_decodes() {
        let value = Secret::OpaqueSetup.generate().unwrap();

        assert_eq!(
            Secret::OpaqueSetup.assess(value.expose_secret()).unwrap(),
            None
        );
    }

    #[test]
    fn generated_opaque_setups_differ() {
        let a = Secret::OpaqueSetup.generate().unwrap();
        let b = Secret::OpaqueSetup.generate().unwrap();

        assert_ne!(a.expose_secret(), b.expose_secret());
    }

    #[test]
    fn random_hex_is_not_an_opaque_setup() {
        assert!(OpaqueSetup::decode(VALID).is_err());
    }

    #[test]
    fn non_hex_opaque_setup_is_rejected() {
        assert!(OpaqueSetup::decode("not hex at all").is_err());
        assert!(OpaqueSetup::decode("").is_err());
    }

    #[test]
    fn generated_invite_pepper_is_valid() {
        let value = Secret::InvitePepper.generate().unwrap();

        assert!(InvitePepper::decode(value.expose_secret()).is_ok());
    }

    #[test]
    fn invite_pepper_must_be_32_bytes() {
        assert!(InvitePepper::decode(VALID).is_ok());
        assert!(InvitePepper::decode(&VALID[2..]).is_err());
        assert!(InvitePepper::decode(&format!("{VALID}00")).is_err());
    }

    #[test]
    fn non_hex_invite_pepper_is_rejected() {
        assert!(InvitePepper::decode("not hex at all").is_err());
        assert!(InvitePepper::decode("").is_err());
    }

    #[test]
    fn create_then_read_round_trips() {
        let (_dir, store) = store();

        let outcome = store.create(Secret::DbPassword, &VALID.into()).unwrap();

        assert_eq!(outcome, CreateOutcome::Created);
        assert_eq!(store.read::<DbPassword>().unwrap().expose_secret(), VALID);
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
    fn read_strips_trailing_line_breaks() {
        let (_dir, store) = store();
        write_raw(
            &store,
            Secret::DbPassword,
            format!("{VALID}\r\n").as_bytes(),
        );

        let value = store.read::<DbPassword>().unwrap();

        assert_eq!(value.expose_secret(), VALID);
    }

    #[test]
    fn read_decodes_stored_opaque_setup() {
        let (_dir, store) = store();
        let stored = OpaqueSetup::generate().unwrap();
        let setup = OpaqueSetup::decode(stored.expose_secret()).unwrap();
        store.create(Secret::OpaqueSetup, &stored).unwrap();

        let read = store.read::<OpaqueSetup>().unwrap();

        assert_eq!(read.public_key(), setup.public_key());
    }

    #[test]
    fn read_returns_db_password_as_stored() {
        let (_dir, store) = store();
        store.create(Secret::DbPassword, &VALID.into()).unwrap();

        let password = store.read::<DbPassword>().unwrap();

        assert_eq!(password.expose_secret(), VALID);
    }

    #[test]
    fn read_rejects_corrupt_opaque_setup() {
        let (_dir, store) = store();
        write_raw(&store, Secret::OpaqueSetup, VALID.as_bytes());

        assert!(store.read::<OpaqueSetup>().is_err());
    }

    #[test]
    fn read_fails_when_missing() {
        let (_dir, store) = store();

        let err = store.read::<DbPassword>().unwrap_err();

        assert_eq!(
            err.downcast_ref::<std::io::Error>().map(|e| e.kind()),
            Some(ErrorKind::NotFound),
        );
    }

    #[test]
    fn read_rejects_empty_value() {
        let (_dir, store) = store();
        write_raw(&store, Secret::DbPassword, b"\r\n");

        assert!(store.read::<DbPassword>().is_err());
    }

    #[test]
    fn read_rejects_non_utf8() {
        let (_dir, store) = store();
        write_raw(&store, Secret::DbPassword, &[0xff; 64]);

        assert!(store.read::<DbPassword>().is_err());
    }

    #[test]
    fn inspect_reports_weak_stored_secret() {
        let (_dir, store) = store();
        write_raw(&store, Secret::DbPassword, b"hunter2");

        let weakness = store.inspect(Secret::DbPassword).unwrap();

        assert_eq!(weakness.as_deref(), Some("shorter than 16 characters"));
    }

    #[test]
    fn inspect_rejects_corrupt_opaque_setup() {
        let (_dir, store) = store();
        write_raw(&store, Secret::OpaqueSetup, VALID.as_bytes());

        assert!(store.inspect(Secret::OpaqueSetup).is_err());
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
