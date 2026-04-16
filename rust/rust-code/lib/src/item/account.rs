use crate::crypto::types::UserId;
use crate::crypto::AccountRootKey;

pub struct Account {
    pub id: UserId,
    pub ark: AccountRootKey,
}

impl Account {
    pub fn new() -> Self {
        Self {
            id: UserId::new_v4(),
            ark: AccountRootKey::generate_random(),
        }
    }
}