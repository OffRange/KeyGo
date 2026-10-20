use crate::error::{OpaqueError, OpaqueResult};
use crate::keys::{ExportKey, SessionKey};
use crate::messages::{
    CredentialFinalization, CredentialRequest, CredentialResponse, RegistrationRequest,
    RegistrationResponse, RegistrationUpload,
};
use crate::suite::{KeyGoSuite, client_login_parameters};
use opaque_ke::ClientRegistrationFinishParameters;
use opaque_ke::keypair::PublicKey;
use rand_core::OsRng;

/// Client state held between registration start and finish.
pub struct ClientRegistration(opaque_ke::ClientRegistration<KeyGoSuite>);

pub struct RegistrationFinish {
    pub upload: RegistrationUpload,
    pub export_key: ExportKey,
    pub server_public_key: Vec<u8>,
}

impl ClientRegistration {
    pub fn start(password: &[u8]) -> OpaqueResult<(Self, RegistrationRequest)> {
        require_password(password)?;
        let result = opaque_ke::ClientRegistration::start(&mut OsRng, password)?;
        Ok((Self(result.state), RegistrationRequest(result.message)))
    }

    pub fn finish(
        self,
        password: &[u8],
        response: RegistrationResponse,
    ) -> OpaqueResult<RegistrationFinish> {
        require_password(password)?;
        let result = self.0.finish(
            &mut OsRng,
            password,
            response.0,
            ClientRegistrationFinishParameters::default(),
        )?;
        Ok(RegistrationFinish {
            upload: RegistrationUpload(result.message),
            export_key: ExportKey::take(result.export_key),
            server_public_key: public_key_bytes(&result.server_s_pk),
        })
    }
}

/// Client state held between KE1 and KE3.
pub struct ClientLogin(opaque_ke::ClientLogin<KeyGoSuite>);

pub struct LoginFinish {
    pub finalization: CredentialFinalization,
    pub session_key: SessionKey,
    pub export_key: ExportKey,
    pub server_public_key: Vec<u8>,
}

impl ClientLogin {
    pub fn start(password: &[u8]) -> OpaqueResult<(Self, CredentialRequest)> {
        require_password(password)?;
        let result = opaque_ke::ClientLogin::start(&mut OsRng, password)?;
        Ok((Self(result.state), CredentialRequest(result.message)))
    }

    pub fn finish(
        self,
        password: &[u8],
        response: CredentialResponse,
    ) -> OpaqueResult<LoginFinish> {
        require_password(password)?;
        let result = self
            .0
            .finish(&mut OsRng, password, response.0, client_login_parameters())?;
        Ok(LoginFinish {
            finalization: CredentialFinalization(result.message),
            session_key: SessionKey::take(result.session_key),
            export_key: ExportKey::take(result.export_key),
            server_public_key: public_key_bytes(&result.server_s_pk),
        })
    }
}

fn require_password(password: &[u8]) -> OpaqueResult<()> {
    if password.is_empty() {
        return Err(OpaqueError::EmptyPassword);
    }
    Ok(())
}

fn public_key_bytes(key: &PublicKey<opaque_ke::Ristretto255>) -> Vec<u8> {
    key.serialize().to_vec()
}
