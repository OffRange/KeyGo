use crate::item::account::Account;
use crate::item::vault::Vault;

pub struct CreateAccount {
    pub account: Account,
    pub default_vault: Vault,
}

impl CreateAccount {
    pub fn generate_new() -> Self {
        Self {
            account: Account::generate_new(),
            default_vault: Vault::generate_new(),
        }
    }
}
