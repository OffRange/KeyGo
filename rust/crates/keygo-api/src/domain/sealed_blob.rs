use keygo_core::{AccountRootKey, KEY_LEN, NONCE_LEN, Sealed, TAG_LEN};
use thiserror::Error;

const WRAPPED_ARK_LEN: usize = NONCE_LEN + KEY_LEN + TAG_LEN;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Error)]
#[error("wrapped key has the wrong length")]
pub struct InvalidBlob;

fn len<T>(blob: &Sealed<T>) -> usize {
    NONCE_LEN + blob.ciphertext().len()
}

pub fn check_wrapped_ark(blob: &Sealed<AccountRootKey>) -> Result<(), InvalidBlob> {
    if len(blob) == WRAPPED_ARK_LEN {
        Ok(())
    } else {
        Err(InvalidBlob)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn blob<T>(len: usize) -> Sealed<T> {
        Sealed::<T>::from_bytes(&vec![7; len]).unwrap()
    }

    /// Clients and stored rows depend on these sizes, so a change to the constants must fail here.
    #[test]
    fn blob_sizes_are_pinned() {
        assert_eq!(WRAPPED_ARK_LEN, 60);
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
}
