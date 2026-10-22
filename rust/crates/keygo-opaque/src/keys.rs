use opaque_ke::generic_array::GenericArray;
use opaque_ke::generic_array::typenum::U64;
use zeroize::{Zeroize, Zeroizing};

pub const KEY_LEN: usize = 64;

/// `$as_bytes` is the accessor's visibility: keep it crate-private for keys whose derivations
/// live in this crate.
macro_rules! secret_key {
    ($name:ident, as_bytes = $as_bytes:vis) => {
        pub struct $name(Zeroizing<[u8; KEY_LEN]>);

        impl $name {
            $as_bytes fn as_bytes(&self) -> &[u8; KEY_LEN] {
                &self.0
            }

            pub(crate) fn take(mut out: GenericArray<u8, U64>) -> Self {
                let mut key = Zeroizing::new([0u8; KEY_LEN]);
                key.copy_from_slice(&out);
                out.as_mut_slice().zeroize();
                Self(key)
            }
        }

        impl core::fmt::Debug for $name {
            fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
                f.write_str(concat!(stringify!($name), "(<redacted>)"))
            }
        }
    };
}

secret_key!(SessionKey, as_bytes = pub(crate));
secret_key!(ExportKey, as_bytes = pub);
