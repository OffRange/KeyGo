use opaque_ke::argon2::{Algorithm, Argon2, Params, Version};
use opaque_ke::errors::InternalError;
use opaque_ke::generic_array::{ArrayLength, GenericArray};
use opaque_ke::ksf::Ksf;
use opaque_ke::{CipherSuite, ClientLoginFinishParameters, ServerLoginParameters};

pub const KSF_MEM_KIB: u32 = 64 * 1024;
pub const KSF_ITERS: u32 = 3;
pub const KSF_LANES: u32 = 4;

/// Binds logins to KeyGo so they can't be replayed against other OPAQUE deployments.
pub const LOGIN_CONTEXT: &[u8] = b"KeyGo-OPAQUE-v1";

pub(crate) struct KeyGoSuite;

impl CipherSuite for KeyGoSuite {
    type OprfCs = opaque_ke::Ristretto255;
    type KeyExchange = opaque_ke::TripleDh<opaque_ke::Ristretto255, sha2::Sha512>;
    type Ksf = KeyGoKsf;
}

pub(crate) struct KeyGoKsf(Argon2<'static>);

impl Default for KeyGoKsf {
    fn default() -> Self {
        let params = Params::new(KSF_MEM_KIB, KSF_ITERS, KSF_LANES, None)
            .expect("pinned Argon2 params are valid");
        Self(Argon2::new(Algorithm::Argon2id, Version::V0x13, params))
    }
}

impl Ksf for KeyGoKsf {
    fn hash<L: ArrayLength<u8>>(
        &self,
        input: GenericArray<u8, L>,
    ) -> Result<GenericArray<u8, L>, InternalError> {
        self.0.hash(input)
    }
}

pub(crate) fn client_login_parameters()
-> ClientLoginFinishParameters<'static, 'static, 'static, KeyGoSuite> {
    ClientLoginFinishParameters {
        context: Some(LOGIN_CONTEXT),
        ..Default::default()
    }
}

pub(crate) fn server_login_parameters() -> ServerLoginParameters<'static, 'static> {
    ServerLoginParameters {
        context: Some(LOGIN_CONTEXT),
        ..Default::default()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ksf_cost_is_pinned() {
        assert_eq!((KSF_MEM_KIB, KSF_ITERS, KSF_LANES), (65_536, 3, 4));
    }

    #[test]
    fn login_context_is_pinned() {
        assert_eq!(LOGIN_CONTEXT, b"KeyGo-OPAQUE-v1");
    }

    #[test]
    fn ksf_uses_pinned_params() {
        let ksf = KeyGoKsf::default();
        let params = ksf.0.params();
        assert_eq!(
            (params.m_cost(), params.t_cost(), params.p_cost()),
            (KSF_MEM_KIB, KSF_ITERS, KSF_LANES),
        );
    }
}
