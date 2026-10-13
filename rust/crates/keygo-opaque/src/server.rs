use crate::error::OpaqueResult;
use crate::keys::SessionKey;
use crate::messages::{
    CredentialFinalization, CredentialRequest, CredentialResponse, PasswordFile,
    RegistrationRequest, RegistrationResponse, RegistrationUpload,
};
use crate::suite::server_login_parameters;
use rand_core::OsRng;
use zeroize::Zeroizing;

opaque_newtype! {
    /// Long-term server keypair and OPRF seed, shared by all accounts.
    ServerSetup(ServerSetup) as Zeroizing<Vec<u8>>;

    /// Server state held between KE2 and KE3.
    ServerLogin(ServerLogin) as Zeroizing<Vec<u8>>;
}

impl ServerSetup {
    pub fn generate() -> Self {
        Self(opaque_ke::ServerSetup::new(&mut OsRng))
    }

    pub fn public_key(&self) -> Vec<u8> {
        self.0.keypair().public().serialize().to_vec()
    }
}

pub fn registration_start(
    setup: &ServerSetup,
    request: RegistrationRequest,
    credential_identifier: &[u8],
) -> OpaqueResult<RegistrationResponse> {
    let result = opaque_ke::ServerRegistration::start(&setup.0, request.0, credential_identifier)?;
    Ok(RegistrationResponse(result.message))
}

pub fn registration_finish(upload: RegistrationUpload) -> PasswordFile {
    PasswordFile(opaque_ke::ServerRegistration::finish(upload.0))
}

impl ServerLogin {
    /// A `None` password file still yields a well-formed response, so unknown accounts fail at the
    /// client exactly like a wrong password.
    pub fn start(
        setup: &ServerSetup,
        password_file: Option<PasswordFile>,
        request: CredentialRequest,
        credential_identifier: &[u8],
    ) -> OpaqueResult<(Self, CredentialResponse)> {
        let result = opaque_ke::ServerLogin::start(
            &mut OsRng,
            &setup.0,
            password_file.map(|file| file.0),
            request.0,
            credential_identifier,
            server_login_parameters(),
        )?;
        Ok((Self(result.state), CredentialResponse(result.message)))
    }

    pub fn finish(self, finalization: CredentialFinalization) -> OpaqueResult<SessionKey> {
        let result = self.0.finish(finalization.0, server_login_parameters())?;
        Ok(SessionKey::take(result.session_key))
    }
}
