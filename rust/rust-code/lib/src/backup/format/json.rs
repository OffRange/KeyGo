use crate::b64;
use crate::backup::encryption::{self, BackupCredential, EncryptionHeader, KeySource};
use crate::backup::{Backup, BackupError, CURRENT_VERSION, MIN_SUPPORTED_VERSION};
use serde::{Deserialize, Serialize};

/// A JSON backup is always sealed - there is no plaintext envelope. `payload` is the base64
/// ciphertext whose header sits in `encryption`.
#[derive(Serialize, Deserialize)]
pub struct BackupEnvelope {
    pub version: u32,
    pub encryption: EncryptionHeader,
    pub payload: String,
}

pub fn export(backup: &Backup, cred: BackupCredential<'_>) -> Result<String, BackupError> {
    let payload_bytes = serde_json::to_vec(backup)?;
    let sealed = encryption::seal(&payload_bytes, cred, CURRENT_VERSION)?;
    let env = BackupEnvelope {
        version: CURRENT_VERSION,
        encryption: sealed.header,
        payload: b64::encode(sealed.ciphertext),
    };
    Ok(serde_json::to_string(&env)?)
}

pub fn import(data: &str, cred: BackupCredential<'_>) -> Result<Backup, BackupError> {
    let env = parse_envelope(data)?;
    let ciphertext = b64::decode(&env.payload).map_err(|_| BackupError::Base64)?;
    let payload_bytes = encryption::open(&env.encryption, &ciphertext, cred, env.version)?;
    Ok(serde_json::from_slice(&payload_bytes)?)
}

/// Report which credential a backup file needs, without decrypting it.
pub fn inspect(data: &str) -> Result<KeySource, BackupError> {
    Ok(parse_envelope(data)?.encryption.source)
}

fn parse_envelope(data: &str) -> Result<BackupEnvelope, BackupError> {
    let env: BackupEnvelope = serde_json::from_str(data)?;
    if !(MIN_SUPPORTED_VERSION..=CURRENT_VERSION).contains(&env.version) {
        return Err(BackupError::UnsupportedVersion(env.version));
    }
    Ok(env)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::backup::encryption::{Kdf, KeySource};
    use crate::backup::{Card, Login, Passkey, Vault};
    use crate::crypto::error::CryptoError;
    use crate::crypto::key::KeyMaterial;
    use crate::crypto::keys::AccountRootKey;

    fn sample_backup() -> Backup {
        Backup {
            vaults: vec![Vault {
                name: "Personal".into(),
                icon: "Work".into(),
                logins: vec![Login {
                    title: "Email".into(),
                    notes: Some("primary".into()),
                    tags: vec!["mail".into()],
                    pinned: true,
                    username: Some("alice".into()),
                    password: Some("s3cr3t-password".into()),
                    totp_secret: None,
                    website: Some("https://mail.example".into()),
                    passkeys: vec![],
                }],
                cards: vec![Card {
                    title: "Visa".into(),
                    notes: None,
                    tags: vec![],
                    pinned: false,
                    cardholder: Some("Alice".into()),
                    number: "4111111111111111".into(),
                    expiration_month: Some(12),
                    expiration_year: Some(2030),
                    cvv: Some("123".into()),
                }],
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
    // to fail loudly if the on-disk format or - critically - the BCS layout of
    // `BackupAad` ever drifts, since either makes existing backups undecryptable
    // with no compile error. Regenerate ONLY alongside a deliberate version bump.
    const GOLDEN_V1_PASSPHRASE: &[u8] = b"golden-pw";
    const GOLDEN_V1: &str = r#"{"version":1,"encryption":{"source":"passphrase","kdf":{"type":"argon2id","salt":"wFYCrxdwhGR19jk2BBUasQ==","mem_kib":65536,"iters":3,"lanes":4},"nonce":"V8dD5ja/CUoL2vYg"},"payload":"GpZBGMDvOgwOPVpst4TnyCMrC+lXMQ8KOPEgeK8GSTZafA/YGBCO+9J7BFmHvtuSuXAaNrlsEviPxFdQCCGJlljW1lJoGJ2Cny0RDlw+75fdH/a4MNwVplSvSJYxeZoYO3wEh8RDyHw3fHlXrQ/u+en1+0psRkdt1Gnvkv+ULxKEOsjTOz0fqzioOYWK/oyNW1h6qJ6x09aTOsBzv1Jv6Wx3vMNzqXGCgFxI9UTzWmdp7hs2JRHHcegTzMaX2cw1lV+shWNpyqEu1anSC6Zc8qfk1vxDj06xGjWZsFeBCfk/8j5Eu5y/c/nDKvcQKC8I3PmPXBBrCmoi/mRDHqu2sMSJQKqBebLv4MkWJUjV3CvfWDJcBHv/ovfNAgmNhNEzZJBA8iC5Pwat0vxopszcsU2bpwg0bPG3hawQkrLkz9sJGnJEsJHSpPSsBj/fS/FqnvkeyrEGH+4TbUqX26LSbFVgmLR3LJtLvP9M3xv99dWsdeqH+C9YmKsXtvXbXCcA20ygL7wc6oCgQbFWGwA7N1lnk1oKDDdaftCu"}"#;

    #[test]
    fn golden_v1_passphrase_still_decrypts() {
        let backup = import(GOLDEN_V1, BackupCredential::Passphrase(GOLDEN_V1_PASSPHRASE)).unwrap();
        // Assert on stable, semantic values so the test survives future additive
        // schema changes (new Option fields) without needing a fresh golden.
        let vault = &backup.vaults[0];
        let login = &vault.logins[0];
        let card = &vault.cards[0];
        assert_eq!(vault.name, "Personal");
        assert_eq!(login.title, "Email");
        assert_eq!(login.password.as_deref(), Some("s3cr3t-password"));
        assert_eq!(login.totp_secret, None);
        assert_eq!(card.number, "4111111111111111");
        assert_eq!(card.expiration_year, Some(2030));
    }

    #[test]
    fn passkeys_round_trip_through_an_encrypted_backup() {
        let mut original = sample_backup();
        original.vaults[0].logins[0].passkeys = vec![
            Passkey {
                user_name: "alice".into(),
                user_display_name: "Alice".into(),
                credential_id: vec![1, 2, 3],
                private_key: vec![4, 5, 6],
                rp: "example.com".into(),
            },
            Passkey {
                user_name: "alice".into(),
                user_display_name: "Alice".into(),
                credential_id: vec![7, 8, 9],
                private_key: vec![10, 11, 12],
                rp: "example.org".into(),
            },
        ];

        let json = export(&original, BackupCredential::Passphrase(b"pw")).unwrap();
        let restored = import(&json, BackupCredential::Passphrase(b"pw")).unwrap();

        let passkeys = &restored.vaults[0].logins[0].passkeys;
        assert_eq!(passkeys.len(), 2);
        assert_eq!(passkeys[0].private_key, vec![4, 5, 6]);
        assert_eq!(passkeys[1].rp, "example.org");
    }

    #[test]
    fn vault_icon_round_trips_through_an_encrypted_backup() {
        let original = sample_backup();

        let json = export(&original, BackupCredential::Passphrase(b"pw")).unwrap();
        let restored = import(&json, BackupCredential::Passphrase(b"pw")).unwrap();

        assert_eq!(restored.vaults[0].icon, "Work");
    }

    #[test]
    fn golden_v1_vault_has_no_icon() {
        // The frozen golden predates the field; serde(default) must read it as an empty string
        // rather than failing the whole import.
        let backup = import(GOLDEN_V1, BackupCredential::Passphrase(GOLDEN_V1_PASSPHRASE)).unwrap();
        assert!(backup.vaults[0].icon.is_empty());
    }

    #[test]
    fn golden_v1_login_has_no_passkeys() {
        // The frozen golden predates the field; serde(default) must read it as an empty list
        // rather than failing the whole import.
        let backup = import(GOLDEN_V1, BackupCredential::Passphrase(GOLDEN_V1_PASSPHRASE)).unwrap();
        assert!(backup.vaults[0].logins[0].passkeys.is_empty());
    }

    #[test]
    fn version_below_minimum_fails() {
        let json = export(&sample_backup(), BackupCredential::Passphrase(b"pw")).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["version"] = serde_json::json!(0);
        let err = import(&v.to_string(), BackupCredential::Passphrase(b"pw")).unwrap_err();
        assert!(matches!(err, BackupError::UnsupportedVersion(0)));
    }

    #[test]
    fn passphrase_round_trip() {
        let original = sample_backup();
        let json = export(&original, BackupCredential::Passphrase(b"pw")).unwrap();
        let restored = import(&json, BackupCredential::Passphrase(b"pw")).unwrap();
        json_eq(&restored, &original);
    }

    // A real v1 ARK-encrypted backup, frozen at generation time, mirroring
    // GOLDEN_V1. It fails loudly if the ARK wire format or HKDF derivation drifts.
    // Regenerate ONLY alongside a deliberate version bump.
    const GOLDEN_V1_ARK_KEY: [u8; 32] = [9u8; 32];
    const GOLDEN_V1_ARK: &str = r#"{"version":1,"encryption":{"source":"ark","kdf":{"type":"hkdf_sha256","salt":"DbSH5vXm1d1XLo2gANptBQ=="},"nonce":"r2uEXqscOq6avV5g"},"payload":"6KlcJQxQ7q2SnHyJ/0YTWEs/GMZk4npIW8+0AUdVyA58gUMk4IUoNYpuCJEZ+6O0SAuaDd68JLpDgcTc3QmSCjxjCpSehOiPXIACk8+yXlb1oS9wlu1+cSuKrNqKJdMdvmSx/3UabH6rznZvN/qyHC6SMK71vtBoG9hgHt7wquYHNv1RIL7Rd5m1BMpmivooS6gfbviYHxVruxSXXSI/KBQ9wjf/XFpZeP5oTubY6snFkfTAJmpDIWY4K/ZVhQX8yLzqFnocRndYpSDHqCn7QbcKpalZcs6vKP5pJUsZl4SuX/3DhOQ1Sn2oHzfpUyVo6TB4+CuZ4fZppnbr4b+/A1kIphHkIMWkNgICnpvS25Yxc4XwtK31ytIa6Ngt2GK5pb6Wh5CS2gRWMWKC6qyexFhZR0UA9ZnczIzp1Y8YuLaTVqk5ZHH63IBqG0Z8pEpohIyyyaX80rmmv4MGwv89+VSCsRsR2+MeiMKe7YA/Gu1FlisLlpiO6Kw4w7P2N2f6JVBgtk/H9aLh3GfsgVHlfNHQzfN6zVXhRffk"}"#;

    #[test]
    fn golden_v1_ark_still_decrypts() {
        let ark = AccountRootKey::try_from_bytes(&GOLDEN_V1_ARK_KEY).unwrap();
        let backup = import(GOLDEN_V1_ARK, BackupCredential::Ark(&ark)).unwrap();
        let vault = &backup.vaults[0];
        let login = &vault.logins[0];
        let card = &vault.cards[0];
        assert_eq!(vault.name, "Personal");
        assert_eq!(login.title, "Email");
        assert_eq!(login.password.as_deref(), Some("s3cr3t-password"));
        assert_eq!(card.number, "4111111111111111");
        assert_eq!(card.expiration_year, Some(2030));
    }

    #[test]
    fn ark_round_trip() {
        let ark = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let original = sample_backup();
        let json = export(&original, BackupCredential::Ark(&ark)).unwrap();
        let restored = import(&json, BackupCredential::Ark(&ark)).unwrap();
        json_eq(&restored, &original);
    }

    #[test]
    fn wrong_passphrase_fails() {
        let json = export(&sample_backup(), BackupCredential::Passphrase(b"right")).unwrap();
        let err = import(&json, BackupCredential::Passphrase(b"wrong")).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn wrong_ark_fails() {
        let right = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let wrong = AccountRootKey::try_from_bytes(&[6u8; 32]).unwrap();
        let json = export(&sample_backup(), BackupCredential::Ark(&right)).unwrap();
        let err = import(&json, BackupCredential::Ark(&wrong)).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::DecryptionFailed),
        ));
    }

    #[test]
    fn empty_passphrase_is_rejected() {
        let err = export(&sample_backup(), BackupCredential::Passphrase(b"")).unwrap_err();
        assert!(matches!(err, BackupError::Crypto(CryptoError::KdfError(_))));
    }

    #[test]
    fn credential_source_mismatch_fails() {
        let ark = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let json = export(&sample_backup(), BackupCredential::Ark(&ark)).unwrap();
        let err = import(&json, BackupCredential::Passphrase(b"x")).unwrap_err();
        assert!(matches!(err, BackupError::CredentialMismatch));
    }

    #[test]
    fn tampered_ciphertext_fails() {
        let json = export(&sample_backup(), BackupCredential::Passphrase(b"pw")).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        let mut ct = b64::decode(v["payload"].as_str().unwrap()).unwrap();
        ct[0] ^= 0x01;
        v["payload"] = serde_json::Value::String(b64::encode(&ct));
        let err = import(&v.to_string(), BackupCredential::Passphrase(b"pw")).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn tampered_header_salt_fails() {
        let json = export(&sample_backup(), BackupCredential::Passphrase(b"pw")).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["encryption"]["kdf"]["salt"] = serde_json::Value::String(b64::encode([0u8; 16]));
        let err = import(&v.to_string(), BackupCredential::Passphrase(b"pw")).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::DecryptionFailed)
        ));
    }

    #[test]
    fn plaintext_envelope_is_rejected() {
        // An envelope with no encryption header is no longer a shape this format admits: the
        // field is required, so a plaintext file fails to parse rather than importing silently.
        let v = serde_json::json!({
            "version": 1,
            "encryption": null,
            "payload": { "vaults": [] },
        });
        let err = import(&v.to_string(), BackupCredential::Passphrase(b"pw")).unwrap_err();
        assert!(matches!(err, BackupError::Json(_)));
    }

    #[test]
    fn inspect_rejects_plaintext_envelope() {
        let v = serde_json::json!({
            "version": 1,
            "encryption": null,
            "payload": { "vaults": [] },
        });
        assert!(matches!(
            inspect(&v.to_string()).unwrap_err(),
            BackupError::Json(_),
        ));
    }

    #[test]
    fn unknown_version_fails() {
        let json = export(&sample_backup(), BackupCredential::Passphrase(b"pw")).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["version"] = serde_json::json!(2);
        let err = import(&v.to_string(), BackupCredential::Passphrase(b"pw")).unwrap_err();
        assert!(matches!(err, BackupError::UnsupportedVersion(2)));
    }

    #[test]
    fn malformed_base64_payload_fails() {
        let json = export(&sample_backup(), BackupCredential::Passphrase(b"pw")).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["payload"] = serde_json::json!("not valid base64!!!");
        let err = import(&v.to_string(), BackupCredential::Passphrase(b"pw")).unwrap_err();
        assert!(matches!(err, BackupError::Base64));
    }

    #[test]
    fn encrypted_envelope_hides_plaintext() {
        let json = export(&sample_backup(), BackupCredential::Passphrase(b"pw")).unwrap();
        let v: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert_eq!(v["encryption"]["source"], serde_json::json!("passphrase"));
        assert!(v["encryption"]["kdf"]["salt"].is_string());
        assert!(v["encryption"]["nonce"].is_string());
        assert!(v["payload"].is_string());
        assert!(!json.contains("s3cr3t-password"));
        assert!(!json.contains("4111111111111111"));
    }

    #[test]
    fn encrypted_envelope_serde_round_trip() {
        let env = BackupEnvelope {
            version: CURRENT_VERSION,
            encryption: EncryptionHeader {
                source: KeySource::Passphrase,
                kdf: Kdf::Argon2id {
                    salt: vec![1u8; 16],
                    mem_kib: 65536,
                    iters: 3,
                    lanes: 4,
                },
                nonce: vec![2u8; 12],
            },
            payload: "AAAA".into(),
        };
        let json = serde_json::to_string(&env).unwrap();
        let back: BackupEnvelope = serde_json::from_str(&json).unwrap();
        assert_eq!(back.payload, "AAAA");
        assert!(matches!(back.encryption.source, KeySource::Passphrase));
        assert!(matches!(back.encryption.kdf, Kdf::Argon2id { .. }));
    }

    #[test]
    fn inspect_reports_passphrase() {
        let json = export(&sample_backup(), BackupCredential::Passphrase(b"pw")).unwrap();
        assert!(matches!(inspect(&json).unwrap(), KeySource::Passphrase));
    }

    #[test]
    fn inspect_reports_ark() {
        let ark = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let json = export(&sample_backup(), BackupCredential::Ark(&ark)).unwrap();
        assert!(matches!(inspect(&json).unwrap(), KeySource::Ark));
    }

    #[test]
    fn inspect_rejects_unsupported_version() {
        let json = export(&sample_backup(), BackupCredential::Passphrase(b"pw")).unwrap();
        let mut v: serde_json::Value = serde_json::from_str(&json).unwrap();
        v["version"] = serde_json::json!(0);
        assert!(matches!(
            inspect(&v.to_string()).unwrap_err(),
            BackupError::UnsupportedVersion(0),
        ));
    }

    #[test]
    fn inspect_rejects_malformed_json() {
        assert!(matches!(inspect("not json").unwrap_err(), BackupError::Json(_)));
    }
}
