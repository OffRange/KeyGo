//! The JSON backup format: a `{ version, encryption, payload }` envelope. Plain
//! backups inline the model as a JSON object; encrypted ones carry a base64
//! ciphertext string with the [`EncryptionHeader`] alongside it.

use crate::backup::encryption::{self, BackupCredential, EncryptionHeader};
use crate::backup::{Backup, BackupError, CURRENT_VERSION, MIN_SUPPORTED_VERSION};
use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use serde::{Deserialize, Serialize};
use std::borrow::Cow;

// One type for both directions: serialize borrows the `Backup` (no clone of the
// vault tree), deserialize lands in `Cow::Owned`.
#[derive(Serialize, Deserialize)]
pub struct BackupEnvelope<'a> {
    pub version: u32,
    pub encryption: Option<EncryptionHeader>,
    pub payload: Payload<'a>,
}

// Variant order is load-bearing: `Encrypted` (a JSON string) must precede `Plain` (a JSON object) for untagged deserialization.
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
                payload: Payload::Encrypted(STANDARD.encode(sealed.ciphertext)),
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
            let ciphertext = STANDARD
                .decode(&ciphertext_b64)
                .map_err(|_| BackupError::Base64)?;
            let payload_bytes = encryption::open(&header, &ciphertext, cred, version)?;
            Ok(serde_json::from_slice(&payload_bytes)?)
        }
        _ => Err(BackupError::EncryptionMismatch),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::backup::encryption::{Cipher, Kdf, KeySource};
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
    const GOLDEN_V1: &str = r#"{"version":1,"encryption":{"source":"passphrase","cipher":"aes-256-gcm-siv","kdf":{"type":"argon2id","salt":"VMzJcsx6gL+Ethd5xleZNQ==","mem_kib":65536,"iters":3,"lanes":4},"nonce":"DOnO4+VQZuqsnbZ2"},"payload":"GbJ2pNmFOT66GKQrppAhUV8mQDObQJRHhimVoiKiJbTntSF4/mdpo4x6RvubuzVxtqUrWvAf7NSxd70YLPhYRfty0+zUvnigJMUtQkOe/EJH3yr/sbAS2zxIK8hQfTHiVDQcXuNfAPbgOOiuwUFs34HTeUBsy++mwP3ivyoPIA2hDeSgSCFJC5J0Vlm1jxt/kE9Vv71WnGsfM0O3XzXB7ik6iJTzJfRx2NDzLmOAkwE6qxrIG2xm5fHVotm0HtMuofoOJHwnlP0klXkg+eLLOADpHKwor0ivbsqFaVlRqsZE4uqQ29+L5cwhTfEb9PWMIzRYvAj+5bQAAlLxzbymyPOIeEQ27deP57oAmjprdBFPcwQ/p2IBpkeX30JyyL6djfA8KIP/ULLGnfLk7PBfQAXYqfsR3DJyQPMJljJv4xEn5JgOj1lvusICxax6vEeG6iX0g3BraTJFOiNSXTfCmWyxbHCXWfgOau7DBGlUwt74cS8D98eRAoj8PNb6KjqLqVUnujSB2qSRUqAR3pB2YHE8tvsl"}"#;

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
    const GOLDEN_V1_ARK: &str = r#"{"version":1,"encryption":{"source":"ark","cipher":"aes-256-gcm-siv","kdf":{"type":"hkdf_sha256","salt":"JrVmjgENfDIdWs2q8Ka66g=="},"nonce":"gDGAG6degpngF2e0"},"payload":"NgURUo9UzuDmzh+D/Bd+9/OCjckyxR4q6kWrF6Sf8UK8xckSShxO6qVNA2PGnePJ2NCgxzko7c2oy2TZ6tALXjwKa35tO7/wJQ4AN9CrgVTfNs1O6s4amOzedkYg5gROIehmqDg/b9BfkWnhyDdyUh7jIAuxt9kCpzwq9ahwAxTQaRizqKHks+X+JKtLS+JGA2uwovbcrHLjBaOMljO5ZO0zcR6WM6y/nRGSw8b53lkjAJhAuXaYRF6iR5RjoIbbxUNGOvvLAI4sI5crxIjGz5AjayRxwHJgiQrpqLrLhVD4FXOqPWIl/DMbbrUwJ+uevbBLqf3TTFxWd6cJ1DXOBXaaZ+pRHasllVG185EsbSD79FsHWLO6L7GT0nEDM6Ho8N3HV2SdX+am1L5fN95lPIApJE3zW9fHhynuLktIFRwtIWjeOIRuYU68iEsWuY1Z+Zdq1hXBeYBhU8R3JDgPVeRx1cdawr8aShlnfxrXNMI84k2rxGP3bHm15hiTIGF7o267FfTofahlZFMJSG/+wuGtx3oc"}"#;

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
        let mut ct = STANDARD.decode(v["payload"].as_str().unwrap()).unwrap();
        ct[0] ^= 0x01;
        v["payload"] = serde_json::Value::String(STANDARD.encode(&ct));
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
        v["encryption"]["kdf"]["salt"] = serde_json::Value::String(STANDARD.encode([0u8; 16]));
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
                "cipher": "aes-256-gcm-siv",
                "kdf": { "type": "argon2id", "salt": STANDARD.encode([1u8; 16]), "mem_kib": 65536, "iters": 3, "lanes": 4 },
                "nonce": STANDARD.encode([2u8; 12]),
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
                cipher: Cipher::Aes256GcmSiv,
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
