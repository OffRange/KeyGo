use crate::item::account::Account;
use crate::item::vault::Vault;

pub struct CreateAccount {
    pub account: Account,
    pub default_vault: Vault,
}

impl CreateAccount {
    pub fn new() -> Self {
        Self {
            account: Account::new(),
            default_vault: Vault::new(),
        }
    }
}