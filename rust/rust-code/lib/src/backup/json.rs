use crate::backup::{Backup, BackupError, BackupKey};
use crate::crypto::keys::AccountRootKey;
use crate::crypto::primitive::aead_data::{AeadCiphertext, AeadEncryptor};
use crate::crypto::random::random_bytes;
use base64::engine::general_purpose::STANDARD;
use base64::Engine;
use serde::{Deserialize, Serialize};
use std::borrow::Cow;

pub const CURRENT_VERSION: u32 = 1;

/// Oldest envelope version this build can still read. Backups are long-lived: a
/// file written by an old build may be restored by a much newer one. When
/// `CURRENT_VERSION` is bumped, keep older versions readable here (and preserve
/// their exact AAD/BCS layout — see `BackupAad`) instead of rejecting them.
/// Per-version decode branches belong in `from_json`, keyed off `env.version`.
pub const MIN_SUPPORTED_VERSION: u32 = 1;

const NONCE_LEN: usize = 12;

const ARGON2_MEM_KIB: u32 = 65536;
const ARGON2_ITERS: u32 = 3;
const ARGON2_LANES: u32 = 4;

#[derive(Serialize, Deserialize)]
pub struct BackupEnvelope<'a> {
    pub version: u32,
    pub encryption: Option<EncryptionHeader>,
    pub payload: Payload<'a>,
}

#[derive(Serialize, Deserialize)]
#[serde(untagged)]
pub enum Payload<'a> {
    Encrypted(String),
    Plain(Cow<'a, Backup>),
}

#[derive(Serialize, Deserialize)]
pub struct EncryptionHeader {
    pub source: KeySource,
    pub cipher: Cipher,
    pub kdf: Kdf,
    #[serde(with = "b64")]
    pub nonce: Vec<u8>,
}

#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum KeySource {
    Passphrase,
    Ark,
}

#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq)]
pub enum Cipher {
    #[serde(rename = "aes-256-gcm-siv")]
    Aes256GcmSiv,
}

#[derive(Serialize, Deserialize, Clone)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Kdf {
    Argon2id {
        #[serde(with = "b64")]
        salt: Vec<u8>,
        mem_kib: u32,
        iters: u32,
        lanes: u32,
    },
}

fn argon2id_kdf(salt: Vec<u8>) -> Kdf {
    Kdf::Argon2id {
        salt,
        mem_kib: ARGON2_MEM_KIB,
        iters: ARGON2_ITERS,
        lanes: ARGON2_LANES,
    }
}

#[derive(Serialize)]
pub struct BackupAad {
    pub version: u32,
    pub source: KeySource,
    pub kdf: Kdf,
}

impl AeadEncryptor for BackupKey {
    type Aad = BackupAad;
}

pub enum BackupCredential<'a> {
    None,
    Passphrase(&'a [u8]),
    Ark(&'a AccountRootKey),
}

impl Backup {
    pub fn to_json(&self, cred: BackupCredential<'_>) -> Result<String, BackupError> {
        match cred {
            BackupCredential::None => {
                let env = BackupEnvelope {
                    version: CURRENT_VERSION,
                    encryption: None,
                    payload: Payload::Plain(Cow::Borrowed(self)),
                };
                Ok(serde_json::to_string(&env)?)
            }
            BackupCredential::Passphrase(passphrase) => {
                let salt = random_bytes::<16>();
                let key = BackupKey::from_passphrase(passphrase, &salt)?;
                self.encrypt_to_json(&key, KeySource::Passphrase, salt.to_vec())
            }
            BackupCredential::Ark(ark) => {
                let salt = random_bytes::<16>();
                let key = BackupKey::from_ark(ark, &salt)?;
                self.encrypt_to_json(&key, KeySource::Ark, salt.to_vec())
            }
        }
    }

    fn encrypt_to_json(
        &self,
        key: &BackupKey,
        source: KeySource,
        salt: Vec<u8>,
    ) -> Result<String, BackupError> {
        let payload_bytes = serde_json::to_vec(self)?;
        let kdf = argon2id_kdf(salt);
        let aad = BackupAad {
            version: CURRENT_VERSION,
            source,
            kdf: kdf.clone(),
        };
        let ciphertext = key.encrypt_data(&payload_bytes, &aad)?;
        let header = EncryptionHeader {
            source,
            cipher: Cipher::Aes256GcmSiv,
            kdf,
            nonce: ciphertext.nonce_bytes().to_vec(),
        };
        let env = BackupEnvelope {
            version: CURRENT_VERSION,
            encryption: Some(header),
            payload: Payload::Encrypted(STANDARD.encode(ciphertext.ciphertext())),
        };
        Ok(serde_json::to_string(&env)?)
    }

    pub fn from_json(json: &str, cred: BackupCredential<'_>) -> Result<Backup, BackupError> {
        let env: BackupEnvelope = serde_json::from_str(json)?;
        let version = env.version;
        if !(MIN_SUPPORTED_VERSION..=CURRENT_VERSION).contains(&version) {
            return Err(BackupError::UnsupportedVersion(version));
        }
        match (env.encryption, env.payload) {
            (None, Payload::Plain(backup)) => {
                if !matches!(cred, BackupCredential::None) {
                    return Err(BackupError::UnexpectedCredential);
                }
                Ok(backup.into_owned())
            }
            (Some(header), Payload::Encrypted(ciphertext_b64)) => {
                decrypt_envelope(header, &ciphertext_b64, cred, version)
            }
            _ => Err(BackupError::EncryptionMismatch),
        }
    }
}

fn decrypt_envelope(
    header: EncryptionHeader,
    ciphertext_b64: &str,
    cred: BackupCredential<'_>,
    version: u32,
) -> Result<Backup, BackupError> {
    let Kdf::Argon2id { ref salt, .. } = header.kdf;

    if header.nonce.len() != NONCE_LEN {
        return Err(BackupError::MalformedHeader);
    }

    let key = match (header.source, &cred) {
        (KeySource::Passphrase, BackupCredential::Passphrase(passphrase)) => {
            BackupKey::from_passphrase(passphrase, salt)?
        }
        (KeySource::Ark, BackupCredential::Ark(ark)) => BackupKey::from_ark(ark, salt)?,
        (_, BackupCredential::None) => return Err(BackupError::MissingCredential),
        _ => return Err(BackupError::CredentialMismatch),
    };

    let aad = BackupAad {
        version,
        source: header.source,
        kdf: header.kdf.clone(),
    };
    let ciphertext = STANDARD
        .decode(ciphertext_b64)
        .map_err(|_| BackupError::Base64)?;
    let parts = AeadCiphertext::<BackupKey>::from_parts_bytes(ciphertext, &header.nonce);
    let payload_bytes = key.decrypt_data(&parts, &aad)?;
    Ok(serde_json::from_slice(&payload_bytes)?)
}

mod b64 {
    use base64::engine::general_purpose::STANDARD;
    use base64::Engine;
    use serde::{Deserialize, Deserializer, Serializer};

    pub fn serialize<S: Serializer>(bytes: &[u8], serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(&STANDARD.encode(bytes))
    }

    pub fn deserialize<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Vec<u8>, D::Error> {
        let s = String::deserialize(deserializer)?;
        STANDARD.decode(s).map_err(serde::de::Error::custom)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::backup::{Card, Login, Vault};
    use crate::crypto::error::CryptoError;
    use crate::crypto::key::KeyMaterial;
    use crate::crypto::keys::AccountRootKey;
    use base64::engine::general_purpose::STANDARD;
    use base64::Engine;
    use serde::{Deserialize, Serialize};

    fn sample_backup() -> Backup {
        Backup {
            vaults: vec![Vault {
                name: "Personal".into(),
                login: Login {
                    title: "Email".into(),
                    notes: Some("primary".into()),
                    tags: vec!["mail".into()],
                    pinned: true,
                    username: Some("alice".into()),
                    password: Some("s3cr3t-password".into()),
                    totp_secret: None,
                    website: Some("https://mail.example".into()),
                    passkey: None,
                },
                card: Card {
                    title: "Visa".into(),
                    notes: None,
                    tags: vec![],
                    pinned: false,
                    cardholder: Some("Alice".into()),
                    number: "4111111111111111".into(),
                    expiration_month: Some(12),
                    expiration_year: Some(2030),
                    cvv: Some("123".into()),
                },
            }],
        }
    }

    fn json_eq(a: &Backup, b: &Backup) {
        assert_eq!(
            serde_json::to_string(a).unwrap(),
            serde_json::to_string(b).unwrap()
        );
    }

    // A real v1 passphrase-encrypted backup, frozen at generation time. Its job is
    // to fail loudly if the on-disk format or — critically — the BCS layout of
    // `BackupAad` ever drifts, since either makes existing backups undecryptable
    // with no compile error. Regenerate ONLY alongside a deliberate version bump.
    const GOLDEN_V1_PASSPHRASE: &[u8] = b"golden-pw";
    const GOLDEN_V1: &str = r#"{"version":1,"encryption":{"source":"passphrase","cipher":"aes-256-gcm-siv","kdf":{"type":"argon2id","salt":"VMzJcsx6gL+Ethd5xleZNQ==","mem_kib":65536,"iters":3,"lanes":4},"nonce":"DOnO4+VQZuqsnbZ2"},"payload":"GbJ2pNmFOT66GKQrppAhUV8mQDObQJRHhimVoiKiJbTntSF4/mdpo4x6RvubuzVxtqUrWvAf7NSxd70YLPhYRfty0+zUvnigJMUtQkOe/EJH3yr/sbAS2zxIK8hQfTHiVDQcXuNfAPbgOOiuwUFs34HTeUBsy++mwP3ivyoPIA2hDeSgSCFJC5J0Vlm1jxt/kE9Vv71WnGsfM0O3XzXB7ik6iJTzJfRx2NDzLmOAkwE6qxrIG2xm5fHVotm0HtMuofoOJHwnlP0klXkg+eLLOADpHKwor0ivbsqFaVlRqsZE4uqQ29+L5cwhTfEb9PWMIzRYvAj+5bQAAlLxzbymyPOIeEQ27deP57oAmjprdBFPcwQ/p2IBpkeX30JyyL6djfA8KIP/ULLGnfLk7PBfQAXYqfsR3DJyQPMJljJv4xEn5JgOj1lvusICxax6vEeG6iX0g3BraTJFOiNSXTfCmWyxbHCXWfgOau7DBGlUwt74cS8D98eRAoj8PNb6KjqLqVUnujSB2qSRUqAR3pB2YHE8tvsl"}"#;

    #[test]
    fn golden_v1_passphrase_still_decrypts() {
        let backup = Backup::from_json(
            GOLDEN_V1,
            BackupCredential::Passphrase(GOLDEN_V1_PASSPHRASE),
        )
            .unwrap();
        // Assert on stable, semantic values so the test survives future additive
        // schema changes (new Option fields) without needing a fresh golden.
        let vault = &backup.vaults[0];
        assert_eq!(vault.name, "Personal");
        assert_eq!(vault.login.title, "Email");
        assert_eq!(vault.login.password.as_deref(), Some("s3cr3t-password"));
        assert_eq!(vault.login.totp_secret, None);
        assert_eq!(vault.card.number, "4111111111111111");
        assert_eq!(vault.card.expiration_year, Some(2030));
    }

    #[test]
    fn version_below_minimum_fails() {
        let json = sample_backup().to_json(BackupCredential::None).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["version"] = serde_json::json!(0);
        let err = Backup::from_json(&v.to_string(), BackupCredential::None).unwrap_err();
        assert!(matches!(err, BackupError::UnsupportedVersion(0)));
    }

    #[test]
    fn plaintext_round_trip() {
        let original = sample_backup();
        let json = original.to_json(BackupCredential::None).unwrap();
        let restored = Backup::from_json(&json, BackupCredential::None).unwrap();
        json_eq(&restored, &original);
    }

    #[test]
    fn passphrase_round_trip() {
        let original = sample_backup();
        let json = original
            .to_json(BackupCredential::Passphrase(b"pw"))
            .unwrap();
        let restored = Backup::from_json(&json, BackupCredential::Passphrase(b"pw")).unwrap();
        json_eq(&restored, &original);
    }

    #[test]
    fn ark_round_trip() {
        let ark = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let original = sample_backup();
        let json = original.to_json(BackupCredential::Ark(&ark)).unwrap();
        let restored = Backup::from_json(&json, BackupCredential::Ark(&ark)).unwrap();
        json_eq(&restored, &original);
    }

    #[test]
    fn wrong_passphrase_fails() {
        let json = sample_backup()
            .to_json(BackupCredential::Passphrase(b"right"))
            .unwrap();
        let err = Backup::from_json(&json, BackupCredential::Passphrase(b"wrong")).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn empty_passphrase_is_rejected() {
        let err = sample_backup()
            .to_json(BackupCredential::Passphrase(b""))
            .unwrap_err();
        assert!(matches!(err, BackupError::Crypto(CryptoError::KdfError(_))));
    }

    #[test]
    fn credential_source_mismatch_fails() {
        let ark = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let json = sample_backup()
            .to_json(BackupCredential::Ark(&ark))
            .unwrap();
        let err = Backup::from_json(&json, BackupCredential::Passphrase(b"x")).unwrap_err();
        assert!(matches!(err, BackupError::CredentialMismatch));
    }

    #[test]
    fn missing_credential_fails() {
        let json = sample_backup()
            .to_json(BackupCredential::Passphrase(b"pw"))
            .unwrap();
        let err = Backup::from_json(&json, BackupCredential::None).unwrap_err();
        assert!(matches!(err, BackupError::MissingCredential));
    }

    #[test]
    fn unexpected_credential_fails() {
        let json = sample_backup().to_json(BackupCredential::None).unwrap();
        let err = Backup::from_json(&json, BackupCredential::Passphrase(b"x")).unwrap_err();
        assert!(matches!(err, BackupError::UnexpectedCredential));
    }

    #[test]
    fn tampered_ciphertext_fails() {
        let json = sample_backup()
            .to_json(BackupCredential::Passphrase(b"pw"))
            .unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        let mut ct = STANDARD.decode(v["payload"].as_str().unwrap()).unwrap();
        ct[0] ^= 0x01;
        v["payload"] = serde_json::Value::String(STANDARD.encode(&ct));
        let err =
            Backup::from_json(&v.to_string(), BackupCredential::Passphrase(b"pw")).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn tampered_header_salt_fails() {
        let json = sample_backup()
            .to_json(BackupCredential::Passphrase(b"pw"))
            .unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["encryption"]["kdf"]["salt"] = serde_json::Value::String(STANDARD.encode([0u8; 16]));
        let err =
            Backup::from_json(&v.to_string(), BackupCredential::Passphrase(b"pw")).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn guard_rejects_null_encryption_with_string_payload() {
        let v = serde_json::json!({ "version": 1, "encryption": null, "payload": "AAAA" });
        let err = Backup::from_json(&v.to_string(), BackupCredential::None).unwrap_err();
        assert!(matches!(err, BackupError::EncryptionMismatch));
    }

    #[test]
    fn guard_rejects_encryption_with_object_payload() {
        let v = serde_json::json!({
            "version": 1,
            "encryption": {
                "source": "passphrase",
                "cipher": "aes-256-gcm-siv",
                "kdf": { "type": "argon2id", "salt": STANDARD.encode([1u8; 16]), "mem_kib": 65536, "iters": 3, "lanes": 4 },
                "nonce": STANDARD.encode([2u8; 12]),
            },
            "payload": { "vaults": [] },
        });
        let err =
            Backup::from_json(&v.to_string(), BackupCredential::Passphrase(b"pw")).unwrap_err();
        assert!(matches!(err, BackupError::EncryptionMismatch));
    }

    #[test]
    fn unknown_version_fails() {
        let json = sample_backup().to_json(BackupCredential::None).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["version"] = serde_json::json!(2);
        let err = Backup::from_json(&v.to_string(), BackupCredential::None).unwrap_err();
        assert!(matches!(err, BackupError::UnsupportedVersion(2)));
    }

    #[test]
    fn malformed_base64_payload_fails() {
        let json = sample_backup()
            .to_json(BackupCredential::Passphrase(b"pw"))
            .unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["payload"] = serde_json::json!("not valid base64!!!");
        let err =
            Backup::from_json(&v.to_string(), BackupCredential::Passphrase(b"pw")).unwrap_err();
        assert!(matches!(err, BackupError::Base64));
    }

    #[test]
    fn plaintext_envelope_shape() {
        let json = sample_backup().to_json(BackupCredential::None).unwrap();
        let v: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert_eq!(v["version"], serde_json::json!(1));
        assert!(v["encryption"].is_null());
        assert!(v["payload"].is_object());
    }

    #[test]
    fn encrypted_envelope_hides_plaintext() {
        let json = sample_backup()
            .to_json(BackupCredential::Passphrase(b"pw"))
            .unwrap();
        let v: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert_eq!(v["encryption"]["source"], serde_json::json!("passphrase"));
        assert!(v["encryption"]["kdf"]["salt"].is_string());
        assert!(v["encryption"]["nonce"].is_string());
        assert!(v["payload"].is_string());
        assert!(!json.contains("s3cr3t-password"));
        assert!(!json.contains("4111111111111111"));
    }

    #[derive(Serialize, Deserialize, PartialEq, Debug)]
    struct Holder {
        #[serde(with = "super::b64")]
        data: Vec<u8>,
    }

    #[test]
    fn b64_round_trip() {
        let h = Holder {
            data: vec![0, 1, 2, 250, 255],
        };
        let json = serde_json::to_string(&h).unwrap();
        assert_eq!(json, r#"{"data":"AAEC+v8="}"#);
        let back: Holder = serde_json::from_str(&json).unwrap();
        assert_eq!(back, h);
    }

    #[test]
    fn key_source_serialization() {
        assert_eq!(
            serde_json::to_value(KeySource::Passphrase).unwrap(),
            serde_json::json!("passphrase")
        );
        assert_eq!(
            serde_json::to_value(KeySource::Ark).unwrap(),
            serde_json::json!("ark")
        );
    }

    #[test]
    fn encryption_header_field_names() {
        let header = EncryptionHeader {
            source: KeySource::Passphrase,
            cipher: Cipher::Aes256GcmSiv,
            kdf: argon2id_kdf(vec![1u8; 16]),
            nonce: vec![2u8; 12],
        };
        let v = serde_json::to_value(&header).unwrap();
        assert_eq!(v["source"], serde_json::json!("passphrase"));
        assert_eq!(v["cipher"], serde_json::json!("aes-256-gcm-siv"));
        assert_eq!(v["kdf"]["type"], serde_json::json!("argon2id"));
        assert!(v["kdf"]["salt"].is_string());
        assert!(v["nonce"].is_string());
    }

    #[test]
    fn plaintext_envelope_serde_round_trip() {
        let env = BackupEnvelope {
            version: CURRENT_VERSION,
            encryption: None,
            payload: Payload::Plain(Cow::Owned(Backup { vaults: vec![] })),
        };
        let json = serde_json::to_string(&env).unwrap();
        let back: BackupEnvelope = serde_json::from_str(&json).unwrap();
        assert!(back.encryption.is_none());
        assert!(matches!(back.payload, Payload::Plain(_)));
    }

    #[test]
    fn backup_key_encrypts_and_decrypts_with_aad() {
        let salt = [4u8; 16];
        let key = BackupKey::from_passphrase(b"pw", &salt).unwrap();
        let aad = BackupAad {
            version: CURRENT_VERSION,
            source: KeySource::Passphrase,
            kdf: argon2id_kdf(salt.to_vec()),
        };
        let ct = key.encrypt_data(b"secret-bytes", &aad).unwrap();
        let pt = key.decrypt_data(&ct, &aad).unwrap();
        assert_eq!(pt, b"secret-bytes");
    }

    #[test]
    fn backup_key_decrypt_fails_with_different_aad() {
        let salt = [4u8; 16];
        let key = BackupKey::from_passphrase(b"pw", &salt).unwrap();
        let aad = BackupAad {
            version: 1,
            source: KeySource::Passphrase,
            kdf: argon2id_kdf(salt.to_vec()),
        };
        let wrong = BackupAad {
            version: 2,
            source: KeySource::Passphrase,
            kdf: argon2id_kdf(salt.to_vec()),
        };
        let ct = key.encrypt_data(b"secret", &aad).unwrap();
        assert!(matches!(
            key.decrypt_data(&ct, &wrong),
            Err(CryptoError::DecryptionFailed),
        ));
    }

    #[test]
    fn encrypted_envelope_serde_round_trip() {
        let env = BackupEnvelope {
            version: CURRENT_VERSION,
            encryption: Some(EncryptionHeader {
                source: KeySource::Passphrase,
                cipher: Cipher::Aes256GcmSiv,
                kdf: argon2id_kdf(vec![1u8; 16]),
                nonce: vec![2u8; 12],
            }),
            payload: Payload::Encrypted("AAAA".into()),
        };
        let json = serde_json::to_string(&env).unwrap();
        let back: BackupEnvelope = serde_json::from_str(&json).unwrap();
        assert!(matches!(back.payload, Payload::Encrypted(_)));
        let header = back.encryption.unwrap();
        assert!(matches!(header.source, KeySource::Passphrase));
        assert!(matches!(header.kdf, Kdf::Argon2id { .. }));
    }
}
