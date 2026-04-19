use lib::crypto::error::CryptoError;
use lib::crypto::item_key::{ItemAad, ItemKey};
use lib::crypto::key::KeyMaterial;
use lib::crypto::keys::{AccountRootKey, RootKEK, VaultKey};
use lib::crypto::primitive::wrap_key::{KeyWrapper as KeyWrapperTrait, WrappedKey};
use lib::crypto::types::{UserId, VaultId};
use std::sync::Arc;

uniffi::custom_type!(RootKEK, Vec<u8>, {
    remote,
    try_lift: |bytes| {
        RootKEK::try_from_bytes(&bytes)
            .map_err(|e| uniffi::deps::anyhow::anyhow!("{e:?}"))
    },
    lower: |key| key.as_bytes().to_vec(),
});

#[derive(uniffi::Record)]
pub struct WrappedKeyBlob {
    pub ciphertext: Vec<u8>,
    pub nonce: Vec<u8>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum KeyWrapError {
    #[error("Key wrap failed")]
    WrapFailed,
    #[error("Key unwrap failed — wrong key or corrupted data")]
    UnwrapFailed,
    #[error("Invalid key material")]
    InvalidKey,
    #[error("Invalid key length: expected {expected} bytes, got {got} bytes")]
    InvalidKeyLength { expected: u64, got: u64 },
    #[error("{0}")]
    Other(String),
}

impl From<CryptoError> for KeyWrapError {
    fn from(value: CryptoError) -> Self {
        match value {
            CryptoError::KeyWrapFailed => Self::WrapFailed,
            CryptoError::KeyUnwrapFailed => Self::UnwrapFailed,
            CryptoError::InvalidKey => Self::InvalidKey,
            CryptoError::InvalidKeyLength { expected, got } => Self::InvalidKeyLength {
                expected: expected as u64,
                got: got as u64,
            },
            other => Self::Other(format!("{other}")),
        }
    }
}

fn wrap<Wrapper, Target>(
    wrapper: &Wrapper,
    target: &Target,
    aad: &Wrapper::Aad,
) -> Result<WrappedKeyBlob, KeyWrapError>
where
    Target: KeyMaterial,
    Wrapper: KeyWrapperTrait<Target>,
{
    let wrapped = wrapper.wrap_key(target, aad)?;
    Ok(WrappedKeyBlob {
        ciphertext: wrapped.ciphertext().to_vec(),
        nonce: wrapped.nonce_bytes().to_vec(),
    })
}

fn unwrap<Wrapper, Target>(
    wrapper: &Wrapper,
    blob: &WrappedKeyBlob,
    aad: &Wrapper::Aad,
) -> Result<Target, KeyWrapError>
where
    Target: KeyMaterial,
    Wrapper: KeyWrapperTrait<Target>,
{
    let wrapped = Wrapper::Wrapped::from_parts_bytes(blob.ciphertext.clone(), &blob.nonce);
    Ok(wrapper.unwrap_key(&wrapped, aad)?)
}

#[derive(uniffi::Object)]
pub struct KeyWrapper;

#[uniffi::export]
impl KeyWrapper {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn wrap_account_root_key(
        &self,
        kek: RootKEK,
        ark: AccountRootKey,
        user_id: UserId,
    ) -> Result<WrappedKeyBlob, KeyWrapError> {
        wrap::<RootKEK, AccountRootKey>(&kek, &ark, &user_id)
    }

    pub fn unwrap_account_root_key(
        &self,
        kek: RootKEK,
        wrapped: WrappedKeyBlob,
        user_id: UserId,
    ) -> Result<AccountRootKey, KeyWrapError> {
        unwrap::<RootKEK, AccountRootKey>(&kek, &wrapped, &user_id)
    }

    pub fn wrap_vault_key(
        &self,
        ark: AccountRootKey,
        vault_key: VaultKey,
        vault_id: VaultId,
    ) -> Result<WrappedKeyBlob, KeyWrapError> {
        wrap::<AccountRootKey, VaultKey>(&ark, &vault_key, &vault_id)
    }

    pub fn unwrap_vault_key(
        &self,
        ark: AccountRootKey,
        wrapped: WrappedKeyBlob,
        vault_id: VaultId,
    ) -> Result<VaultKey, KeyWrapError> {
        unwrap::<AccountRootKey, VaultKey>(&ark, &wrapped, &vault_id)
    }

    pub fn wrap_item_key(
        &self,
        vault_key: VaultKey,
        item_key: ItemKey,
        aad: ItemAad,
    ) -> Result<WrappedKeyBlob, KeyWrapError> {
        wrap::<VaultKey, ItemKey>(&vault_key, &item_key, &aad)
    }

    pub fn unwrap_item_key(
        &self,
        vault_key: VaultKey,
        wrapped: WrappedKeyBlob,
        aad: ItemAad,
    ) -> Result<ItemKey, KeyWrapError> {
        unwrap::<VaultKey, ItemKey>(&vault_key, &wrapped, &aad)
    }
}
