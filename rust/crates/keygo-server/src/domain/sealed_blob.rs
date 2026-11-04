use keygo_core::{KEY_LEN, NONCE_LEN, Sealed, TAG_LEN};
use thiserror::Error;

pub type SealedBlob = Sealed<[u8]>;

const WRAPPED_ARK_LEN: usize = NONCE_LEN + KEY_LEN + TAG_LEN;
// The plaintext's layout belongs to the client, so only the nonce and the tag are a known minimum.
const MIN_WRAPPED_PRIVATE_KEYS_LEN: usize = NONCE_LEN + TAG_LEN;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Error)]
#[error("wrapped key has the wrong length")]
pub struct InvalidBlob;

fn len(blob: &SealedBlob) -> usize {
    NONCE_LEN + blob.ciphertext().len()
}

pub fn check_wrapped_ark(blob: &SealedBlob) -> Result<(), InvalidBlob> {
    if len(blob) == WRAPPED_ARK_LEN {
        Ok(())
    } else {
        Err(InvalidBlob)
    }
}

pub fn check_wrapped_private_keys(blob: &SealedBlob) -> Result<(), InvalidBlob> {
    if len(blob) >= MIN_WRAPPED_PRIVATE_KEYS_LEN {
        Ok(())
    } else {
        Err(InvalidBlob)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn blob(len: usize) -> SealedBlob {
        SealedBlob::from_bytes(&vec![7; len]).unwrap()
    }

    /// Clients and stored rows depend on these sizes, so a change to the constants must fail here.
    #[test]
    fn blob_sizes_are_pinned() {
        assert_eq!(WRAPPED_ARK_LEN, 60);
        assert_eq!(MIN_WRAPPED_PRIVATE_KEYS_LEN, 28);
    }

    #[test]
    fn a_wrapped_ark_has_one_exact_length() {
        assert_eq!(check_wrapped_ark(&blob(WRAPPED_ARK_LEN)), Ok(()));
        assert_eq!(
            check_wrapped_ark(&blob(WRAPPED_ARK_LEN - 1)),
            Err(InvalidBlob),
        );
        assert_eq!(
            check_wrapped_ark(&blob(WRAPPED_ARK_LEN + 1)),
            Err(InvalidBlob),
        );
    }

    #[test]
    fn a_wrapped_ark_cannot_be_only_a_nonce() {
        assert_eq!(check_wrapped_ark(&blob(NONCE_LEN)), Err(InvalidBlob));
    }

    #[test]
    fn wrapped_private_keys_need_at_least_a_nonce_and_a_tag() {
        assert_eq!(
            check_wrapped_private_keys(&blob(MIN_WRAPPED_PRIVATE_KEYS_LEN)),
            Ok(()),
        );
        assert_eq!(
            check_wrapped_private_keys(&blob(MIN_WRAPPED_PRIVATE_KEYS_LEN - 1)),
            Err(InvalidBlob),
        );
    }

    #[test]
    fn wrapped_private_keys_have_no_fixed_length() {
        assert_eq!(
            check_wrapped_private_keys(&blob(MIN_WRAPPED_PRIVATE_KEYS_LEN + 2 * KEY_LEN)),
            Ok(()),
        );
    }
}
