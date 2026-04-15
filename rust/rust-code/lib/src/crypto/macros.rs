#[macro_export]
macro_rules! define_wrap {
    ($wrapper:ident => $key:path, aad = $aad:path) => {
        impl $crate::crypto::primitive::wrap_key::KeyWrapper<$key> for $wrapper {
            type Aad = $aad;
            type Wrapped = $crate::crypto::primitive::wrap_key::AeadWrappedKey<$key, Self>;
        }
    };
}

#[macro_export]
macro_rules! define_scoped_signing_key {
    (wrapper = $wrapper:ident, key = $key:ident, wrapped_key = $wrapped:ident, aad = $aad:path $(,)?) => {
        pub type $key = $crate::crypto::keys::signing_key::ScopedSigningKey<$wrapper>;
        pub type $wrapped = <$wrapper as $crate::crypto::primitive::wrap_key::KeyWrapper<
            ::ed25519_dalek::SigningKey,
        >>::Wrapped;

        $crate::define_wrap!($wrapper => ::ed25519_dalek::SigningKey, aad = $aad);

        impl $wrapper {
            pub fn wrap_signing_key(
                &self,
                signing_key: $key,
                aad: &$aad,
            ) -> $crate::crypto::error::CryptoResult<$wrapped> {
                signing_key.wrapped_signing_key(aad, self)
            }

            pub fn unwrap_signing_key(
                &self,
                wrapped_key: &$wrapped,
                aad: &$aad,
            ) -> $crate::crypto::error::CryptoResult<$key> {
                $key::unwrap_signing_key(wrapped_key, aad, self)
            }
        }
    };
}

#[macro_export]
macro_rules! define_aead_key {
    (
        $(#[$meta:meta])*
        random $vis:vis struct $name:ident($algo:ident);
    ) => {
        $crate::define_aead_key! {
            $(#[$meta])*
            $vis struct $name($algo);
        }

        impl $name {
            pub fn generate_random() -> Self {
                let key = <::aead::Key::<$algo> as ::aead::Generate>::generate();
                Self(key)
            }
        }
    };

    (
        $(#[$meta:meta])*
        $vis:vis struct $name:ident($algo:ident);
    ) => {
        $(#[$meta])*
        #[derive(::zeroize::Zeroize, ::zeroize::ZeroizeOnDrop)]
        $vis struct $name(::aead::Key<$algo>);

        impl $crate::crypto::key::AeadKey for $name {
            type Algorithm = $algo;

            fn key(&self) -> &::aead::Key<Self::Algorithm> {
                &self.0
            }
        }

        impl $crate::crypto::key::KeyMaterial for $name {
            fn try_from_bytes(bytes: &[u8]) -> $crate::crypto::error::CryptoResult<Self> {
            let key =
                ::aead::Key::<$algo>::try_from(bytes).map_err(|_| $crate::crypto::error::CryptoError::InvalidKeyLength {
                    expected: <<$algo as ::aead::KeySizeUser>::KeySize as ::aead::array::typenum::Unsigned>::USIZE,
                    got: bytes.len(),
                })?;

                Ok(Self(key))
            }

            fn as_bytes(&self) -> &[u8] {
                self.0.as_slice()
            }
        }
    };
}
