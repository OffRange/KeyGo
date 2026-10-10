use thiserror::Error;

#[derive(Debug, Error)]
pub enum CryptoError {
    #[error("AEAD encryption failed")]
    EncryptionFailed,
    #[error("AEAD decryption failed: ciphertext invalid or tampered")]
    DecryptionFailed,

    #[error("Key wrap failed")]
    KeyWrapFailed,
    #[error("Key unwrap failed: wrong key or corrupted data")]
    KeyUnwrapFailed,

    #[error("BCS serialisation failed: {0}")]
    BCS(String),

    #[error("Signature verification failed")]
    SignatureInvalid,

    #[error("Invalid key material")]
    InvalidKey,

    #[error("Key derivation failed: {0}")]
    KdfError(String),
    #[error("Invalid key length: expected {expected} bytes, got {got} bytes")]
    InvalidKeyLength { expected: usize, got: usize },
}

pub type CryptoResult<T> = Result<T, CryptoError>;
