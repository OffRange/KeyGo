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

    #[error("Invalid key length: expected {expected} bytes, got {got} bytes")]
    InvalidKeyLength { expected: usize, got: usize },
    #[error("Invalid nonce length: expected {expected} bytes, got {got} bytes")]
    InvalidNonceLength { expected: usize, got: usize },

    #[error("AAD encoding failed: {0}")]
    AadEncoding(#[from] bcs::Error),

    #[error("Password must not be empty")]
    EmptyPassword,
    #[error("Salt too short: expected at least {min} bytes, got {got} bytes")]
    SaltTooShort { min: usize, got: usize },
    #[error("Argon2 memory cost too high: {got} KiB exceeds the {max} KiB limit")]
    Argon2MemoryTooHigh { max: u32, got: u32 },
    #[error("Argon2 iteration count too high: {got} exceeds the {max} limit")]
    Argon2ItersTooHigh { max: u32, got: u32 },
    #[error("Argon2 failed: {0}")]
    Argon2(argon2::Error),
}

pub type CryptoResult<T> = Result<T, CryptoError>;
