use crate::b64;
use crate::backup::BackupError;
use crate::backup::BackupKey;
use crate::crypto::kdf::argon2::Argon2Params;
use crate::crypto::kdf::new_salt;
use crate::crypto::{AccountRootKey, Encrypts, Sealed, aad};
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
pub struct EncryptionHeader {
    pub source: KeySource,
    pub kdf: Kdf,
    #[serde(with = "b64")]
    pub nonce: Vec<u8>,
}

#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, Debug)]
#[serde(rename_all = "snake_case")]
pub enum KeySource {
    Passphrase,
    Ark,
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

impl Kdf {
    fn argon2id(salt: Vec<u8>, params: Argon2Params) -> Self {
        Kdf::Argon2id {
            salt,
            mem_kib: params.mem_kib,
            iters: params.iters,
            lanes: params.lanes,
        }
    }

    fn hkdf_sha256(salt: Vec<u8>) -> Self {
        Kdf::HkdfSha256 { salt }
    }
}

aad! {
    /// Wire format: change only with a version bump.
    pub struct BackupAad {
        pub version: u32,
        pub source: KeySource,
    }
}

impl Encrypts for BackupKey {
    type Aad = BackupAad;
}

#[derive(Clone, Copy)]
pub enum BackupCredential<'a> {
    Passphrase(&'a [u8]),
    Ark(&'a AccountRootKey),
}

pub struct SealedPayload {
    pub header: EncryptionHeader,
    pub ciphertext: Vec<u8>,
}

pub fn seal(
    plaintext: &[u8],
    cred: BackupCredential<'_>,
    version: u32,
) -> Result<SealedPayload, BackupError> {
    let salt = new_salt();
    let (backup_key, source, kdf) = match cred {
        BackupCredential::Passphrase(passphrase) => {
            let argon_param = Argon2Params::default();
            (
                BackupKey::from_passphrase(passphrase, &salt, argon_param)?,
                KeySource::Passphrase,
                Kdf::argon2id(salt.to_vec(), argon_param),
            )
        }
        BackupCredential::Ark(ark) => (
            BackupKey::from_ark(ark, &salt)?,
            KeySource::Ark,
            Kdf::hkdf_sha256(salt.to_vec()),
        ),
    };
    let aad = BackupAad { version, source };
    let (nonce, ciphertext) = backup_key.encrypt(plaintext, &aad)?.into_parts();
    Ok(SealedPayload {
        header: EncryptionHeader { source, kdf, nonce },
        ciphertext,
    })
}

pub fn open(
    header: &EncryptionHeader,
    ciphertext: Vec<u8>,
    cred: BackupCredential<'_>,
    version: u32,
) -> Result<Vec<u8>, BackupError> {
    // Before key derivation, which may run Argon2.
    let sealed =
        Sealed::from_parts(&header.nonce, ciphertext).map_err(|_| BackupError::MalformedHeader)?;

    let key = match (header.source, &header.kdf, cred) {
        (KeySource::Passphrase, _, BackupCredential::Ark(_))
        | (KeySource::Ark, _, BackupCredential::Passphrase(_)) => {
            return Err(BackupError::CredentialMismatch);
        }

        (
            KeySource::Passphrase,
            Kdf::Argon2id {
                salt,
                mem_kib,
                iters,
                lanes,
            },
            BackupCredential::Passphrase(passphrase),
        ) => BackupKey::from_passphrase(
            passphrase,
            salt,
            Argon2Params {
                mem_kib: *mem_kib,
                iters: *iters,
                lanes: *lanes,
            },
        )?,
        (KeySource::Ark, Kdf::HkdfSha256 { salt }, BackupCredential::Ark(ark)) => {
            BackupKey::from_ark(ark, salt)?
        }

        // KDF does not match the source.
        (KeySource::Passphrase, _, BackupCredential::Passphrase(_))
        | (KeySource::Ark, _, BackupCredential::Ark(_)) => {
            return Err(BackupError::MalformedHeader);
        }
    };

    let aad = BackupAad {
        version,
        source: header.source,
    };
    Ok(key.decrypt(&sealed, &aad)?)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::backup::CURRENT_VERSION;
    use crate::crypto::kdf::argon2::MAX_ARGON2_MEM_KIB;
    use crate::crypto::{CryptoError, SymmetricKey};

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
            kdf: Kdf::argon2id(vec![1u8; 16], Argon2Params::default()),
            nonce: vec![2u8; 12],
        };
        let v = serde_json::to_value(&header).unwrap();
        assert_eq!(v["source"], serde_json::json!("passphrase"));
        assert_eq!(v["kdf"]["type"], serde_json::json!("argon2id"));
        assert!(v["kdf"]["salt"].is_string());
        assert!(v["nonce"].is_string());
    }

    #[test]
    fn backup_key_encrypts_and_decrypts_with_aad() {
        let salt = [4u8; 16];
        let key = BackupKey::from_passphrase(b"pw", &salt, Argon2Params::default()).unwrap();
        let aad = BackupAad {
            version: CURRENT_VERSION,
            source: KeySource::Passphrase,
        };
        let ct = key.encrypt(b"secret-bytes", &aad).unwrap();
        let pt = key.decrypt(&ct, &aad).unwrap();
        assert_eq!(pt, b"secret-bytes");
    }

    #[test]
    fn backup_key_decrypt_fails_with_different_aad() {
        let salt = [4u8; 16];
        let key = BackupKey::from_passphrase(b"pw", &salt, Argon2Params::default()).unwrap();
        let aad = BackupAad {
            version: 1,
            source: KeySource::Passphrase,
        };
        let wrong = BackupAad {
            version: 2,
            source: KeySource::Passphrase,
        };
        let ct = key.encrypt(b"secret", &aad).unwrap();
        assert!(matches!(
            key.decrypt(&ct, &wrong),
            Err(CryptoError::DecryptionFailed),
        ));
    }

    #[test]
    fn seal_then_open_round_trips() {
        let cred = BackupCredential::Passphrase(b"pw");
        let sealed = seal(b"hello-bytes", cred, CURRENT_VERSION).unwrap();
        let pt = open(&sealed.header, sealed.ciphertext, cred, CURRENT_VERSION).unwrap();
        assert_eq!(pt, b"hello-bytes");
    }

    #[test]
    fn open_rejects_wrong_nonce_length() {
        let cred = BackupCredential::Passphrase(b"pw");
        let mut sealed = seal(b"x", cred, CURRENT_VERSION).unwrap();
        sealed.header.nonce.push(0);
        let err = open(&sealed.header, sealed.ciphertext, cred, CURRENT_VERSION).unwrap_err();
        assert!(matches!(err, BackupError::MalformedHeader));
    }

    #[test]
    fn ark_seal_records_hkdf_kdf() {
        let ark = AccountRootKey::from_bytes(&[5u8; 32]).unwrap();
        let sealed = seal(b"x", BackupCredential::Ark(&ark), CURRENT_VERSION).unwrap();
        assert!(matches!(sealed.header.source, KeySource::Ark));
        assert!(matches!(sealed.header.kdf, Kdf::HkdfSha256 { .. }));
    }

    #[test]
    fn ark_seal_then_open_round_trips() {
        let ark = AccountRootKey::from_bytes(&[5u8; 32]).unwrap();
        let cred = BackupCredential::Ark(&ark);
        let sealed = seal(b"hello-ark", cred, CURRENT_VERSION).unwrap();
        let pt = open(&sealed.header, sealed.ciphertext, cred, CURRENT_VERSION).unwrap();
        assert_eq!(pt, b"hello-ark");
    }

    #[test]
    fn open_rejects_ark_source_with_argon2id_kdf() {
        let ark = AccountRootKey::from_bytes(&[5u8; 32]).unwrap();
        let cred = BackupCredential::Ark(&ark);
        let mut sealed = seal(b"x", cred, CURRENT_VERSION).unwrap();
        sealed.header.kdf = Kdf::argon2id(vec![1u8; 16], Argon2Params::default());
        let err = open(&sealed.header, sealed.ciphertext, cred, CURRENT_VERSION).unwrap_err();
        assert!(matches!(err, BackupError::MalformedHeader));
    }

    #[test]
    fn open_rejects_passphrase_source_with_hkdf_kdf() {
        let cred = BackupCredential::Passphrase(b"pw");
        let mut sealed = seal(b"x", cred, CURRENT_VERSION).unwrap();
        sealed.header.kdf = Kdf::hkdf_sha256(vec![1u8; 16]);
        let err = open(&sealed.header, sealed.ciphertext, cred, CURRENT_VERSION).unwrap_err();
        assert!(matches!(err, BackupError::MalformedHeader));
    }

    #[test]
    fn open_rejects_argon2_params_exceeding_memory_limit() {
        let cred = BackupCredential::Passphrase(b"pw");
        let mut sealed = seal(b"x", cred, CURRENT_VERSION).unwrap();
        if let Kdf::Argon2id { mem_kib, .. } = &mut sealed.header.kdf {
            *mem_kib = MAX_ARGON2_MEM_KIB + 1;
        }
        let err = open(&sealed.header, sealed.ciphertext, cred, CURRENT_VERSION).unwrap_err();
        assert!(matches!(
            err,
            BackupError::Crypto(CryptoError::Argon2MemoryTooHigh { .. })
        ));
    }
}
