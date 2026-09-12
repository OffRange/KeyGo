use keygo_core::backup::{Backup, Card, Login, Passkey, Vault};

#[uniffi::remote(Record)]
#[uniffi(name = "BackupPasskey")]
struct Passkey {
    pub user_name: String,
    pub user_display_name: String,
    pub credential_id: Vec<u8>,
    pub private_key: Vec<u8>,
    pub rp: String,
}

#[uniffi::remote(Record)]
#[uniffi(name = "BackupLogin")]
struct Login {
    pub title: String,
    pub notes: Option<String>,
    pub tags: Vec<String>,
    pub pinned: bool,
    pub username: Option<String>,
    pub password: Option<String>,
    pub totp_secret: Option<String>,
    pub websites: Vec<String>,
    pub passkeys: Vec<Passkey>,
}

#[uniffi::remote(Record)]
#[uniffi(name = "BackupCard")]
struct Card {
    pub title: String,
    pub notes: Option<String>,
    pub tags: Vec<String>,
    pub pinned: bool,
    pub cardholder: Option<String>,
    pub number: String,
    pub expiration_month: Option<u8>,
    pub expiration_year: Option<u16>,
    pub cvv: Option<String>,
}

#[uniffi::remote(Record)]
#[uniffi(name = "BackupVault")]
struct Vault {
    pub name: String,
    pub icon: String,
    pub logins: Vec<Login>,
    pub cards: Vec<Card>,
}

#[uniffi::remote(Record)]
struct Backup {
    pub vaults: Vec<Vault>,
}
