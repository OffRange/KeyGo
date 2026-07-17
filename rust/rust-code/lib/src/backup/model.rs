use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Backup {
    pub vaults: Vec<Vault>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Vault {
    pub name: String,
    pub logins: Vec<Login>,
    pub cards: Vec<Card>,
}

macro_rules! backup_item {
    (
        $(#[$meta:meta])*
        $vis:vis struct $name:ident {
            $(
                $(#[$field_meta:meta])*
                $field_vis:vis $field:ident : $field_ty:ty
            ),*$(,)?
        }
    ) => {
        $(#[$meta])*

        #[derive(Debug, Clone, Default, Serialize, Deserialize)]
        $vis struct $name {
            pub title: String,
            pub notes: Option<String>,
            pub tags: Vec<String>,
            pub pinned: bool,
            $(
                $(#[$field_meta])*
                $field_vis $field: $field_ty,
            )*
        }
    };
}

backup_item! {
    pub struct Login {
        pub username: Option<String>,
        pub password: Option<String>,
        pub totp_secret: Option<String>,
        pub website: Option<String>,
        /// A login can hold several passkeys (one per RP). `default` keeps pre-field backups -
        /// including the frozen v1 goldens - readable.
        #[serde(default)]
        pub passkeys: Vec<Passkey>,
   }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Passkey {
    pub user_name: String,
    pub user_display_name: String,
    pub credential_id: Vec<u8>,
    pub private_key: Vec<u8>,
    pub rp: String,
}

backup_item! {
    pub struct Card {
        pub cardholder: Option<String>,
        pub number: String,
        pub expiration_month: Option<u8>,
        pub expiration_year: Option<u16>,
        pub cvv: Option<String>,
    }
}
