use crate::error::{CryptoError, CryptoResult};
use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use std::marker::PhantomData;

pub const KEY_LEN: usize = 32;
pub const NONCE_LEN: usize = 12;
pub const TAG_LEN: usize = 16;

pub struct Sealed<T: ?Sized> {
    pub(crate) nonce: [u8; NONCE_LEN],
    pub(crate) ciphertext: Vec<u8>,
    pub(crate) content: PhantomData<fn() -> Box<T>>,
}

impl<T> From<Sealed<T>> for Sealed<[u8]> {
    fn from(value: Sealed<T>) -> Self {
        Self {
            nonce: value.nonce,
            ciphertext: value.ciphertext,
            content: PhantomData,
        }
    }
}

impl<T> From<Sealed<[u8]>> for Sealed<T> {
    fn from(value: Sealed<[u8]>) -> Self {
        Self {
            nonce: value.nonce,
            ciphertext: value.ciphertext,
            content: PhantomData,
        }
    }
}

impl<T: ?Sized> Sealed<T> {
    pub fn from_parts(nonce: &[u8], ciphertext: Vec<u8>) -> CryptoResult<Self> {
        let nonce = nonce
            .try_into()
            .map_err(|_| CryptoError::InvalidNonceLength {
                expected: NONCE_LEN,
                got: nonce.len(),
            })?;

        Ok(Self {
            nonce,
            ciphertext,
            content: PhantomData,
        })
    }

    pub fn nonce(&self) -> &[u8] {
        self.nonce.as_slice()
    }

    pub fn ciphertext(&self) -> &[u8] {
        &self.ciphertext
    }

    /// `(nonce, ciphertext)`
    pub fn into_parts(self) -> (Vec<u8>, Vec<u8>) {
        (self.nonce.to_vec(), self.ciphertext)
    }

    pub fn to_bytes(&self) -> Vec<u8> {
        let mut out = Vec::with_capacity(NONCE_LEN + self.ciphertext.len());
        out.extend_from_slice(&self.nonce);
        out.extend_from_slice(&self.ciphertext);
        out
    }

    pub fn from_bytes(bytes: &[u8]) -> CryptoResult<Self> {
        let (nonce, ciphertext) =
            bytes
                .split_first_chunk::<NONCE_LEN>()
                .ok_or(CryptoError::InvalidNonceLength {
                    expected: NONCE_LEN,
                    got: bytes.len(),
                })?;

        Ok(Self {
            nonce: *nonce,
            ciphertext: ciphertext.to_vec(),
            content: PhantomData,
        })
    }
}

impl<T: ?Sized> Serialize for Sealed<T> {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        URL_SAFE_NO_PAD
            .encode(self.to_bytes())
            .serialize(serializer)
    }
}

impl<'de, T: ?Sized> Deserialize<'de> for Sealed<T> {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let data = String::deserialize(deserializer)?;
        let data = URL_SAFE_NO_PAD
            .decode(data)
            .map_err(serde::de::Error::custom)?;
        Self::from_bytes(&data).map_err(serde::de::Error::custom)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::VaultKey;

    #[test]
    fn from_parts_rejects_a_wrong_length_nonce() {
        assert!(matches!(
            Sealed::<[u8]>::from_parts(&[0; 8], vec![]),
            Err(CryptoError::InvalidNonceLength {
                expected: NONCE_LEN,
                got: 8,
            })
        ));
    }

    #[test]
    fn parts_round_trip() {
        let sealed = Sealed::<[u8]>::from_parts(&[7; NONCE_LEN], vec![1, 2, 3]).unwrap();
        assert_eq!(sealed.into_parts(), (vec![7; NONCE_LEN], vec![1, 2, 3]));
    }

    #[test]
    fn a_wrapped_key_is_nameable_in_every_build() {
        let wrapped = Sealed::<VaultKey>::from_parts(&[7; NONCE_LEN], vec![1, 2, 3]).unwrap();
        assert_eq!(wrapped.nonce(), [7; NONCE_LEN]);
        assert_eq!(wrapped.ciphertext(), [1, 2, 3]);
    }

    fn json(value: &str) -> String {
        format!(r#""{value}""#)
    }

    fn parse(value: &str) -> serde_json::Result<Sealed<[u8]>> {
        serde_json::from_str(&json(value))
    }

    #[test]
    fn bytes_are_the_nonce_then_the_ciphertext() {
        let sealed = Sealed::<[u8]>::from_parts(&[7; NONCE_LEN], vec![1, 2, 3]).unwrap();
        assert_eq!(
            sealed.to_bytes(),
            [[7; NONCE_LEN].as_slice(), &[1, 2, 3]].concat()
        );
    }

    #[test]
    fn bytes_round_trip() {
        let bytes = [[7; NONCE_LEN].as_slice(), &[1, 2, 3]].concat();
        let sealed = Sealed::<[u8]>::from_bytes(&bytes).unwrap();

        assert_eq!(sealed.nonce(), [7; NONCE_LEN]);
        assert_eq!(sealed.ciphertext(), [1, 2, 3]);
        assert_eq!(sealed.to_bytes(), bytes);
    }

    #[test]
    fn from_bytes_rejects_a_blob_too_short_for_a_nonce() {
        assert!(matches!(
            Sealed::<[u8]>::from_bytes(&[7; NONCE_LEN - 1]),
            Err(CryptoError::InvalidNonceLength {
                expected: NONCE_LEN,
                got: 11,
            })
        ));
    }

    /// The wire form is API identity: changing this string breaks every client and stored blob.
    #[test]
    fn json_form_is_pinned() {
        let sealed = Sealed::<[u8]>::from_parts(&[7; NONCE_LEN], vec![1, 2, 3]).unwrap();
        assert_eq!(
            serde_json::to_string(&sealed).unwrap(),
            json("BwcHBwcHBwcHBwcHAQID"),
        );
    }

    #[test]
    fn json_round_trip() {
        let parsed = parse("BwcHBwcHBwcHBwcHAQID").unwrap();
        assert_eq!(parsed.into_parts(), (vec![7; NONCE_LEN], vec![1, 2, 3]));
    }

    #[test]
    fn json_uses_the_url_safe_alphabet() {
        let parsed = parse("BwcHBwcHBwcHBwcH-w").unwrap();
        assert_eq!(parsed.ciphertext(), [0xfb]);
    }

    #[test]
    fn json_rejects_the_standard_alphabet() {
        assert!(parse("BwcHBwcHBwcHBwcH+w").is_err());
    }

    #[test]
    fn json_rejects_padding() {
        assert!(parse("BwcHBwcHBwcHBwcH-w==").is_err());
    }

    #[test]
    fn json_rejects_a_blob_too_short_for_a_nonce() {
        assert!(parse("BwcHBwcHBwcHBwc").is_err());
    }
}
