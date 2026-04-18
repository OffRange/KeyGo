use lib::crypto::KeyMaterial;
use lib::crypto::error::CryptoError;
use lib::crypto::item_key::{ItemAad, ItemKey};
use lib::crypto::primitive::aead_data::{AeadCiphertext, AeadEncryptor};
use lib::crypto::types::{ItemId, VaultId};
use std::sync::Arc;

uniffi::custom_type!(ItemKey, Vec<u8>, {
    remote,
    try_lift: |bytes| {
        ItemKey::try_from_bytes(&bytes)
            .map_err(|e| uniffi::deps::anyhow::anyhow!("{e:?}"))
    },
    lower: |key| key.as_bytes().to_vec(),
});

#[uniffi::remote(Record)]
pub struct ItemAad {
    pub item_id: ItemId,
    pub vault_id: VaultId,
}

#[derive(uniffi::Record)]
pub struct EncryptedItemBlob {
    pub ciphertext: Vec<u8>,
    pub nonce: Vec<u8>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ItemCryptoError {
    #[error("Encryption failed")]
    EncryptionFailed,
    #[error("Decryption failed — ciphertext invalid or tampered")]
    DecryptionFailed,
    #[error("Invalid key material")]
    InvalidKey,
    #[error("Invalid key length: expected {expected} bytes, got {got} bytes")]
    InvalidKeyLength { expected: u64, got: u64 },
    #[error("{0}")]
    Other(String),
}

impl From<CryptoError> for ItemCryptoError {
    fn from(value: CryptoError) -> Self {
        match value {
            CryptoError::EncryptionFailed => Self::EncryptionFailed,
            CryptoError::DecryptionFailed => Self::DecryptionFailed,
            CryptoError::InvalidKey => Self::InvalidKey,
            CryptoError::InvalidKeyLength { expected, got } => Self::InvalidKeyLength {
                expected: expected as u64,
                got: got as u64,
            },
            other => Self::Other(format!("{other}")),
        }
    }
}

#[derive(uniffi::Object)]
pub struct ItemManager;

#[uniffi::export]
impl ItemManager {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn create_new_item_key(&self) -> ItemKey {
        ItemKey::generate_random()
    }

    pub fn encrypt_item_data(
        &self,
        item_key: ItemKey,
        data: Vec<u8>,
        aad: ItemAad,
    ) -> Result<EncryptedItemBlob, ItemCryptoError> {
        let ct = item_key.encrypt_data(&data, &aad)?;
        Ok(EncryptedItemBlob {
            ciphertext: ct.ciphertext().to_vec(),
            nonce: ct.nonce_bytes().to_vec(),
        })
    }

    pub fn decrypt_item_data(
        &self,
        item_key: ItemKey,
        blob: EncryptedItemBlob,
        aad: ItemAad,
    ) -> Result<Vec<u8>, ItemCryptoError> {
        let ciphertext = AeadCiphertext::<ItemKey>::from_parts_bytes(blob.ciphertext, &blob.nonce);
        Ok(item_key.decrypt_data(&ciphertext, &aad)?)
    }
}
