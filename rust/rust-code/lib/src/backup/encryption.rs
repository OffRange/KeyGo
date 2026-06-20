use crate::backup::key::BackupKey;
use crate::backup::BackupError;
use crate::crypto::keys::AccountRootKey;
use crate::crypto::primitive::aead_data::{AeadCiphertext, AeadEncryptor};
use crate::crypto::random::random_bytes;
use serde::{Deserialize, Serialize};

const NONCE_LEN: usize = 12;

const ARGON2_MEM_KIB: u32 = 65536;
const ARGON2_ITERS: u32 = 3;
const ARGON2_LANES: u32 = 4;

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
    HkdfSha256 {
        #[serde(with = "b64")]
        salt: Vec<u8>,
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

fn hkdf_sha256_kdf(salt: Vec<u8>) -> Kdf {
    Kdf::HkdfSha256 { salt }
}

/// Additional data bound to every backup ciphertext. It is never stored — it is
/// rebuilt from the header at open time — so its BCS layout is a frozen wire
/// format (see the `golden_v1_passphrase_still_decrypts` test). Evolve it only
/// via a version bump, never by editing fields in place.
#[derive(Serialize)]
pub struct BackupAad {
    pub version: u32,
    pub source: KeySource,
    pub kdf: Kdf,
}

impl AeadEncryptor for BackupKey {
    type Aad = BackupAad;
}

/// User secret supplied when reading or writing a backup. The credential is
/// optional at the API boundary: a `None` credential (see [`open`] and the
/// formats' `export`/`import`) means "no secret", i.e. a plaintext backup. This
/// type therefore only ever names the variants that actually derive a key, so
/// [`seal`] is total — it never has to reject a "no credential" case.
#[derive(Clone, Copy)]
pub enum BackupCredential<'a> {
    Passphrase(&'a [u8]),
    Ark(&'a AccountRootKey),
}

/// Result of [`seal`]: the header to record and the raw ciphertext to frame.
pub struct SealedPayload {
    pub header: EncryptionHeader,
    pub ciphertext: Vec<u8>,
}

/// Encrypts `plaintext` under a freshly derived key, returning the header that
/// describes the derivation and the raw ciphertext. Format-agnostic: the caller
/// frames `header` + `ciphertext` however its format requires.
pub fn seal(
    plaintext: &[u8],
    cred: BackupCredential<'_>,
    version: u32,
) -> Result<SealedPayload, BackupError> {
    let salt = random_bytes::<16>();
    let (backup_key, source, kdf) = match cred {
        BackupCredential::Passphrase(passphrase) => (
            BackupKey::from_passphrase(passphrase, &salt)?,
            KeySource::Passphrase,
            argon2id_kdf(salt.to_vec()),
        ),
        BackupCredential::Ark(ark) => (
            BackupKey::from_ark(ark, &salt)?,
            KeySource::Ark,
            hkdf_sha256_kdf(salt.to_vec()),
        ),
    };
    let aad = BackupAad {
        version,
        source,
        kdf: kdf.clone(),
    };
    let ciphertext = backup_key.encrypt_data(plaintext, &aad)?;
    Ok(SealedPayload {
        header: EncryptionHeader {
            source,
            cipher: Cipher::Aes256GcmSiv,
            kdf,
            nonce: ciphertext.nonce_bytes().to_vec(),
        },
        ciphertext: ciphertext.ciphertext().to_vec(),
    })
}

/// Re-derives the key from `header` + `cred`, rebuilds the AAD bound at seal time,
/// and returns the decrypted plaintext. `version` must be the envelope's own
/// version so the rebuilt AAD matches byte-for-byte.
pub fn open(
    header: &EncryptionHeader,
    ciphertext: &[u8],
    cred: Option<BackupCredential<'_>>,
    version: u32,
) -> Result<Vec<u8>, BackupError> {
    if header.nonce.len() != NONCE_LEN {
        return Err(BackupError::MalformedHeader);
    }

    let key = match (header.source, &header.kdf, cred) {
        // No credential supplied for an encrypted backup.
        (_, _, None) => return Err(BackupError::MissingCredential),

        // Credential's source disagrees with the header's declared source.
        (KeySource::Passphrase, _, Some(BackupCredential::Ark(_)))
        | (KeySource::Ark, _, Some(BackupCredential::Passphrase(_))) => {
            return Err(BackupError::CredentialMismatch)
        }

        // Source matches credential and the KDF matches the source: derive.
        (
            KeySource::Passphrase,
            Kdf::Argon2id { salt, .. },
            Some(BackupCredential::Passphrase(passphrase)),
        ) => BackupKey::from_passphrase(passphrase, salt)?,
        (KeySource::Ark, Kdf::HkdfSha256 { salt }, Some(BackupCredential::Ark(ark))) => {
            BackupKey::from_ark(ark, salt)?
        }

        // Source matches credential but the KDF disagrees with the source.
        (KeySource::Passphrase, _, Some(BackupCredential::Passphrase(_)))
        | (KeySource::Ark, _, Some(BackupCredential::Ark(_))) => {
            return Err(BackupError::MalformedHeader)
        }
    };

    let aad = BackupAad {
        version,
        source: header.source,
        kdf: header.kdf.clone(),
    };
    let parts = AeadCiphertext::<BackupKey>::from_parts_bytes(ciphertext.to_vec(), &header.nonce);
    Ok(key.decrypt_data(&parts, &aad)?)
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
    use crate::backup::CURRENT_VERSION;
    use crate::crypto::error::CryptoError;
    use crate::crypto::key::KeyMaterial;
    use crate::crypto::keys::AccountRootKey;
    use serde::{Deserialize, Serialize};

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
    fn seal_then_open_round_trips() {
        let cred = BackupCredential::Passphrase(b"pw");
        let sealed = seal(b"hello-bytes", cred, CURRENT_VERSION).unwrap();
        let pt = open(
            &sealed.header,
            &sealed.ciphertext,
            Some(cred),
            CURRENT_VERSION,
        )
            .unwrap();
        assert_eq!(pt, b"hello-bytes");
    }

    #[test]
    fn open_rejects_wrong_nonce_length() {
        let cred = BackupCredential::Passphrase(b"pw");
        let mut sealed = seal(b"x", cred, CURRENT_VERSION).unwrap();
        sealed.header.nonce.push(0);
        let err = open(
            &sealed.header,
            &sealed.ciphertext,
            Some(cred),
            CURRENT_VERSION,
        )
            .unwrap_err();
        assert!(matches!(err, BackupError::MalformedHeader));
    }

    #[test]
    fn ark_seal_records_hkdf_kdf() {
        let ark = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let sealed = seal(b"x", BackupCredential::Ark(&ark), CURRENT_VERSION).unwrap();
        assert!(matches!(sealed.header.source, KeySource::Ark));
        assert!(matches!(sealed.header.kdf, Kdf::HkdfSha256 { .. }));
    }

    #[test]
    fn ark_seal_then_open_round_trips() {
        let ark = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let cred = BackupCredential::Ark(&ark);
        let sealed = seal(b"hello-ark", cred, CURRENT_VERSION).unwrap();
        let pt = open(&sealed.header, &sealed.ciphertext, Some(cred), CURRENT_VERSION).unwrap();
        assert_eq!(pt, b"hello-ark");
    }

    #[test]
    fn open_rejects_ark_source_with_argon2id_kdf() {
        let ark = AccountRootKey::try_from_bytes(&[5u8; 32]).unwrap();
        let cred = BackupCredential::Ark(&ark);
        let mut sealed = seal(b"x", cred, CURRENT_VERSION).unwrap();
        // Header still says source=Ark but now carries a passphrase-style KDF.
        sealed.header.kdf = argon2id_kdf(vec![1u8; 16]);
        let err = open(&sealed.header, &sealed.ciphertext, Some(cred), CURRENT_VERSION).unwrap_err();
        assert!(matches!(err, BackupError::MalformedHeader));
    }

    #[test]
    fn open_rejects_passphrase_source_with_hkdf_kdf() {
        let cred = BackupCredential::Passphrase(b"pw");
        let mut sealed = seal(b"x", cred, CURRENT_VERSION).unwrap();
        sealed.header.kdf = hkdf_sha256_kdf(vec![1u8; 16]);
        let err = open(&sealed.header, &sealed.ciphertext, Some(cred), CURRENT_VERSION).unwrap_err();
        assert!(matches!(err, BackupError::MalformedHeader));
    }
}
