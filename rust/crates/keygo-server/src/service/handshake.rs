use crate::cache::{LoginStore, Pending};
use crate::domain::LoginId;
use keygo_opaque::server::{ServerLogin, ServerSetup};
use keygo_opaque::{
    CredentialFinalization, CredentialRequest, CredentialResponse, DpopBinding, OpaqueError,
    PasswordFile,
};
use std::time::Duration;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("login expired or unknown")]
    Expired,
    #[error(transparent)]
    Opaque(#[from] OpaqueError),
    #[error("database error")]
    Db(#[from] sqlx::Error),
}

pub struct Started {
    pub login_id: LoginId,
    pub credential_response: CredentialResponse,
    pub expires_in: Duration,
}

pub(super) fn begin<U: Clone + Send + Sync + 'static>(
    states: &LoginStore<U>,
    setup: &ServerSetup,
    user_id: U,
    password_file: Option<PasswordFile>,
    identifier: &[u8],
    credential_request: CredentialRequest,
) -> Result<Started, Error> {
    let (state, credential_response) =
        ServerLogin::start(setup, password_file, credential_request, identifier)?;

    let login_id = LoginId::random();
    states.insert(login_id, Pending { user_id, state });

    Ok(Started {
        login_id,
        credential_response,
        expires_in: states.ttl(),
    })
}

pub(super) fn complete<U: Clone + Send + Sync + 'static>(
    states: &LoginStore<U>,
    login_id: &LoginId,
    credential_finalization: CredentialFinalization,
    binding: &DpopBinding,
    jkt: &[u8; 32],
) -> Result<U, Error> {
    let pending = states.take(login_id).ok_or(Error::Expired)?;
    let session_key = pending.state.finish(credential_finalization)?;
    session_key.verify_dpop_binding(jkt, binding)?;
    Ok(pending.user_id)
}
