pub mod argon2;
pub(crate) mod hkdf;

use aead::Generate;

pub const SALT_LEN: usize = 16;

pub fn new_salt() -> [u8; SALT_LEN] {
    <[u8; SALT_LEN]>::generate()
}
