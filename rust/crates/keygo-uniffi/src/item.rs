use keygo_core::crypto::{
    CryptoError, Encrypts, ItemAad, ItemDataAad, ItemId, ItemKey, Sealed, SecretKind, VaultId,
};
use std::sync::Arc;

#[uniffi::remote(Record)]
pub struct ItemAad {
    pub item_id: ItemId,
    pub vault_id: VaultId,
}

#[uniffi::remote(Record)]
pub struct ItemDataAad {
    pub item_id: ItemId,
    pub kind: SecretKind,
}

#[uniffi::remote(Enum)]
pub enum SecretKind {
    Password,
    CardNumber,
    Cvv,
    PasskeyPrivateKey,
    TotpSecret,
    PasswordHealthReport,
}

#[derive(uniffi::Record)]
pub struct EncryptedItemBlob {
    pub ciphertext: Vec<u8>,
    pub nonce: Vec<u8>,
}

impl From<Sealed<[u8]>> for EncryptedItemBlob {
    fn from(sealed: Sealed<[u8]>) -> Self {
        let (nonce, ciphertext) = sealed.into_parts();
        Self { ciphertext, nonce }
    }
}

impl EncryptedItemBlob {
    fn into_sealed(self) -> Result<Sealed<[u8]>, CryptoError> {
        Sealed::from_parts(&self.nonce, self.ciphertext)
    }
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ItemCryptoError {
    #[error("Encryption failed")]
    EncryptionFailed,
    #[error("Decryption failed: ciphertext invalid or tampered")]
    DecryptionFailed,
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
            CryptoError::InvalidKeyLength { expected, got } => Self::InvalidKeyLength {
                expected: expected as u64,
                got: got as u64,
            },
            other => Self::Other(other.to_string()),
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
        ItemKey::generate()
    }

    pub fn encrypt_item_data(
        &self,
        item_key: ItemKey,
        data: Vec<u8>,
        aad: ItemDataAad,
    ) -> Result<EncryptedItemBlob, ItemCryptoError> {
        Ok(item_key.encrypt(&data, &aad)?.into())
    }

    pub fn decrypt_item_data(
        &self,
        item_key: ItemKey,
        blob: EncryptedItemBlob,
        aad: ItemDataAad,
    ) -> Result<Vec<u8>, ItemCryptoError> {
        Ok(item_key.decrypt(&blob.into_sealed()?, &aad)?)
    }
}
