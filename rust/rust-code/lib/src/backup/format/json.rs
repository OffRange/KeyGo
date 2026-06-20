use crate::b64;
use crate::backup::encryption::{self, BackupCredential, EncryptionHeader};
use crate::backup::{Backup, BackupError, CURRENT_VERSION, MIN_SUPPORTED_VERSION};
use serde::{Deserialize, Serialize};
use std::borrow::Cow;

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

pub fn export(backup: &Backup, cred: Option<BackupCredential<'_>>) -> Result<String, BackupError> {
    let env = match cred {
        None => BackupEnvelope {
            version: CURRENT_VERSION,
            encryption: None,
            payload: Payload::Plain(Cow::Borrowed(backup)),
        },
        Some(cred) => {
            let payload_bytes = serde_json::to_vec(backup)?;
            let sealed = encryption::seal(&payload_bytes, cred, CURRENT_VERSION)?;
            BackupEnvelope {
                version: CURRENT_VERSION,
                encryption: Some(sealed.header),
                payload: Payload::Encrypted(b64::encode(sealed.ciphertext)),
            }
        }
    };
    Ok(serde_json::to_string(&env)?)
}

pub fn import(data: &str, cred: Option<BackupCredential<'_>>) -> Result<Backup, BackupError> {
    let env: BackupEnvelope = serde_json::from_str(data)?;
    let version = env.version;
    if !(MIN_SUPPORTED_VERSION..=CURRENT_VERSION).contains(&version) {
        return Err(BackupError::UnsupportedVersion(version));
    }
    match (env.encryption, env.payload) {
        (None, Payload::Plain(backup)) => {
            if cred.is_some() {
                return Err(BackupError::UnexpectedCredential);
            }
            Ok(backup.into_owned())
        }
        (Some(header), Payload::Encrypted(ciphertext_b64)) => {
            let ciphertext = b64::decode(&ciphertext_b64).map_err(|_| BackupError::Base64)?;
            let payload_bytes = encryption::open(&header, &ciphertext, cred, version)?;
            Ok(serde_json::from_slice(&payload_bytes)?)
        }
        _ => Err(BackupError::EncryptionMismatch),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::backup::encryption::{Kdf, KeySource};
    use crate::backup::{Card, Login, Vault};
    use crate::crypto::error::CryptoError;
    use crate::crypto::key::KeyMaterial;
    use crate::crypto::keys::AccountRootKey;

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
    const GOLDEN_V1: &str = r#"{"version":1,"encryption":{"source":"passphrase","kdf":{"type":"argon2id","salt":"VbS6tQR2HzeV+PyWZv/xPQ==","mem_kib":65536,"iters":3,"lanes":4},"nonce":"xuKErpOzQtIUTF0W"},"payload":"qJUkSI20qk4htPhPS6tXAhISVK86ikQM+OA02MEh3KNT8kqj6iZ2TEjf47QZ2pKbl7oho5HeRObSZW6N0CAu1F/LMHWpBlUPelKgFcOgGXnLJLrTXzN1aDjlo1U6NGWXCJZJl3B0P82amVWzlEuiUaA22NND4hb3p+BHZ34WjJFdw9uSz5KIGv3PofI7MO7lmRa43CSTJHAJjCyUn5/21ply8TfegD1Tf0TcKGXCL5WdRruBYrDBHK1BoV4LjO5eU6RewvGRP3h3SCz2IAGEj/H5reOI0fKPUzA9zGesVMAJB1RnLUys7H+RzRGPQ/P4ViVil6IAKMJF2iiim4yypjeLzLo5F+toQKUAxbKSaRNMvxBdmJVloq+Uucn3rs4IgxwDkXBTUb+IlptQrpQpg3HE2RkjKesQtZK3hMwCMWfPO8g7wLsINNvvLR/pSe4QdvsoEaqVTIzJUlbM0KGxYa7gSbHSNdzRUVfkPApegIqFWiQbfMq5CfOw6WBxZR8MDzo6Djj4a0kSpSxyh9yr5YENiQ1/"}"#;

    #[test]
    fn golden_v1_passphrase_still_decrypts() {
        let backup = import(
            GOLDEN_V1,
            Some(BackupCredential::Passphrase(GOLDEN_V1_PASSPHRASE)),
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
        let json = export(&sample_backup(), None).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["version"] = serde_json::json!(0);
        let err = import(&v.to_string(), None).unwrap_err();
        assert!(matches!(err, BackupError::UnsupportedVersion(0)));
    }

    #[test]
    fn plaintext_round_trip() {
        let original = sample_backup();
        let json = export(&original, None).unwrap();
        let restored = import(&json, None).unwrap();
        json_eq(&restored, &original);
    }

    #[test]
    fn passphrase_round_trip() {
        let original = sample_backup();
        let json = export(&original, Some(BackupCredential::Passphrase(b"pw"))).unwrap();
        let restored = import(&json, Some(BackupCredential::Passphrase(b"pw"))).unwrap();
        json_eq(&restored, &original);
    }

    // A real v1 ARK-encrypted backup, frozen at generation time, mirroring
    // GOLDEN_V1. It fails loudly if the ARK wire format or HKDF derivation drifts.
    // Regenerate ONLY alongside a deliberate version bump.
    const GOLDEN_V1_ARK_KEY: [u8; 32] = [9u8; 32];
    const GOLDEN_V1_ARK: &str = r#"{"version":1,"encryption":{"source":"ark","kdf":{"type":"hkdf_sha256","salt":"4RYbuO3VfhzEleupHEHXeQ=="},"nonce":"yiEZW5gcp8GWXDz4"},"payload":"uviKJ4uxZgkNjbJqPO2HwCrEZe6wkAGuN/HFR2XAx1Sx3wO0fbiV6kNGB7BdlSr/npuWE6vX6vue5bFKb22Wj1AUQ7ON/jOg09b+qPLQm46DwlsysR4FGpteyoVBpCP6CYeddcjyr4ipdDlud0xzeVEWqly64m8X2CWUQaEpfrulBxfAnt0DE/CUV0OcrSNfNs4W7C9lyR2OuBb8RjSMCX+E07gNCRwdwuQNkef5lGbz4Bd5RXI3EesfRVCfEYvvxVdWemzBiGfuwLk8BwaEieRVdj+1mZ5v1kRMNrbgy21qoGQdE+AUF6zvmNoktl6MfXTQFyFzufUwQtNZLJvIoUBKMn4b6AFa+h+n3Aq1QJR/AotW9/u9TMIAHL4q8U+OrLmTcPwx9vOkXc6U6xWVsOT/8I0pxxHklfU8Rjf4mNzULe7BTC9FXbib/GUkXlkL9W5UOvzWYEPjA0In6FPUKHFdtrdiLsH7cfL2E/Qz8rI268bQoh05DnlOxoEjdMPvAzEbSOOiL9scZiTOLp5xc3DRY3VO"}"#;

    #[test]
    fn golden_v1_ark_still_decrypts() {
        let ark = AccountRootKey::try_from_bytes(&GOLDEN_V1_ARK_KEY).unwrap();
        let backup = import(GOLDEN_V1_ARK, Some(BackupCredential::Ark(&ark))).unwrap();
        let vault = &backup.vaults[0];
        assert_eq!(vault.name, "Personal");
        assert_eq!(vault.login.title, "Email");
        assert_eq!(vault.login.password.as_deref(), Some("s3cr3t-password"));
        assert_eq!(vault.card.number, "4111111111111111");
        assert_eq!(vault.card.expiration_year, Some(2030));
    }

    #[test]
    fn ark_round_trip() {
        let ark = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let original = sample_backup();
        let json = export(&original, Some(BackupCredential::Ark(&ark))).unwrap();
        let restored = import(&json, Some(BackupCredential::Ark(&ark))).unwrap();
        json_eq(&restored, &original);
    }

    #[test]
    fn wrong_passphrase_fails() {
        let json = export(
            &sample_backup(),
            Some(BackupCredential::Passphrase(b"right")),
        )
        .unwrap();
        let err = import(&json, Some(BackupCredential::Passphrase(b"wrong"))).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn wrong_ark_fails() {
        let right = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let wrong = AccountRootKey::try_from_bytes(&[6u8; 32]).unwrap();
        let json = export(&sample_backup(), Some(BackupCredential::Ark(&right))).unwrap();
        let err = import(&json, Some(BackupCredential::Ark(&wrong))).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::DecryptionFailed),
        ));
    }

    #[test]
    fn empty_passphrase_is_rejected() {
        let err = export(&sample_backup(), Some(BackupCredential::Passphrase(b""))).unwrap_err();
        assert!(matches!(err, BackupError::Crypto(CryptoError::KdfError(_))));
    }

    #[test]
    fn credential_source_mismatch_fails() {
        let ark = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let json = export(&sample_backup(), Some(BackupCredential::Ark(&ark))).unwrap();
        let err = import(&json, Some(BackupCredential::Passphrase(b"x"))).unwrap_err();
        assert!(matches!(err, BackupError::CredentialMismatch));
    }

    #[test]
    fn missing_credential_fails() {
        let json = export(&sample_backup(), Some(BackupCredential::Passphrase(b"pw"))).unwrap();
        let err = import(&json, None).unwrap_err();
        assert!(matches!(err, BackupError::MissingCredential));
    }

    #[test]
    fn unexpected_credential_fails() {
        let json = export(&sample_backup(), None).unwrap();
        let err = import(&json, Some(BackupCredential::Passphrase(b"x"))).unwrap_err();
        assert!(matches!(err, BackupError::UnexpectedCredential));
    }

    #[test]
    fn tampered_ciphertext_fails() {
        let json = export(&sample_backup(), Some(BackupCredential::Passphrase(b"pw"))).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        let mut ct = b64::decode(v["payload"].as_str().unwrap()).unwrap();
        ct[0] ^= 0x01;
        v["payload"] = serde_json::Value::String(b64::encode(&ct));
        let err = import(&v.to_string(), Some(BackupCredential::Passphrase(b"pw"))).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn tampered_header_salt_fails() {
        let json = export(&sample_backup(), Some(BackupCredential::Passphrase(b"pw"))).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["encryption"]["kdf"]["salt"] = serde_json::Value::String(b64::encode([0u8; 16]));
        let err = import(&v.to_string(), Some(BackupCredential::Passphrase(b"pw"))).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn guard_rejects_null_encryption_with_string_payload() {
        let v = serde_json::json!({ "version": 1, "encryption": null, "payload": "AAAA" });
        let err = import(&v.to_string(), None).unwrap_err();
        assert!(matches!(err, BackupError::EncryptionMismatch));
    }

    #[test]
    fn guard_rejects_encryption_with_object_payload() {
        let v = serde_json::json!({
            "version": 1,
            "encryption": {
                "source": "passphrase",
                "kdf": { "type": "argon2id", "salt": b64::encode([1u8; 16]), "mem_kib": 65536, "iters": 3, "lanes": 4 },
                "nonce": b64::encode([2u8; 12]),
            },
            "payload": { "vaults": [] },
        });
        let err = import(&v.to_string(), Some(BackupCredential::Passphrase(b"pw"))).unwrap_err();
        assert!(matches!(err, BackupError::EncryptionMismatch));
    }

    #[test]
    fn unknown_version_fails() {
        let json = export(&sample_backup(), None).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["version"] = serde_json::json!(2);
        let err = import(&v.to_string(), None).unwrap_err();
        assert!(matches!(err, BackupError::UnsupportedVersion(2)));
    }

    #[test]
    fn malformed_base64_payload_fails() {
        let json = export(&sample_backup(), Some(BackupCredential::Passphrase(b"pw"))).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["payload"] = serde_json::json!("not valid base64!!!");
        let err = import(&v.to_string(), Some(BackupCredential::Passphrase(b"pw"))).unwrap_err();
        assert!(matches!(err, BackupError::Base64));
    }

    #[test]
    fn plaintext_envelope_shape() {
        let json = export(&sample_backup(), None).unwrap();
        let v: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert_eq!(v["version"], serde_json::json!(1));
        assert!(v["encryption"].is_null());
        assert!(v["payload"].is_object());
    }

    #[test]
    fn encrypted_envelope_hides_plaintext() {
        let json = export(&sample_backup(), Some(BackupCredential::Passphrase(b"pw"))).unwrap();
        let v: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert_eq!(v["encryption"]["source"], serde_json::json!("passphrase"));
        assert!(v["encryption"]["kdf"]["salt"].is_string());
        assert!(v["encryption"]["nonce"].is_string());
        assert!(v["payload"].is_string());
        assert!(!json.contains("s3cr3t-password"));
        assert!(!json.contains("4111111111111111"));
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
    fn encrypted_envelope_serde_round_trip() {
        let env = BackupEnvelope {
            version: CURRENT_VERSION,
            encryption: Some(EncryptionHeader {
                source: KeySource::Passphrase,
                kdf: Kdf::Argon2id {
                    salt: vec![1u8; 16],
                    mem_kib: 65536,
                    iters: 3,
                    lanes: 4,
                },
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
