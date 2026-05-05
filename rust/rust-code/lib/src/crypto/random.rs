use rand::Rng;
use rand::rand_core::UnwrapErr;
use rand::rngs::SysRng;

/// Generate a random byte array of the specified length.
pub fn random_bytes<const N: usize>() -> [u8; N] {
    let mut buf = [0u8; N];
    UnwrapErr(&mut SysRng).fill_bytes(&mut buf);
    buf
}
