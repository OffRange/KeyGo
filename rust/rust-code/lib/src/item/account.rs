use crate::crypto::AccountRootKey;
use crate::crypto::types::UserId;

pub struct Account {
    pub id: UserId,
    pub ark: AccountRootKey,
}

impl Account {
    pub fn generate_new() -> Self {
        Self {
            id: UserId::new_v4(),
            ark: AccountRootKey::generate_random(),
        }
    }
}
