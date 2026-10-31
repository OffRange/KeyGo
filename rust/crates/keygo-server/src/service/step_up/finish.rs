use crate::cache::StepUpStates;
use crate::db::sessions;
use crate::domain::{AccessToken, LoginId, SessionId, TokenIssuer, UserId, unix_now};
use crate::service::handshake::Error;
use keygo_opaque::{CredentialFinalization, DpopBinding};
use sqlx::PgPool;

pub struct FinishStepUp {
    pub session_id: SessionId,
    pub user_id: UserId,
    pub login_id: LoginId,
    pub credential_finalization: CredentialFinalization,
    pub binding: DpopBinding,
    pub jkt: [u8; 32],
}

pub struct IssuedStepUp {
    pub access_token: AccessToken,
    pub auth_time: u64,
}

pub async fn finish(
    db: &PgPool,
    step_up_states: &StepUpStates,
    token_issuer: &TokenIssuer,
    input: FinishStepUp,
) -> Result<IssuedStepUp, Error> {
    let FinishStepUp {
        session_id,
        user_id,
        login_id,
        credential_finalization,
        binding,
        jkt,
    } = input;

    let pending = step_up_states.take(&login_id).ok_or(Error::Expired)?;
    let session_key = pending.state.finish(credential_finalization)?;
    session_key.verify_dpop_binding(&jkt, &binding)?;

    let auth_time = unix_now();
    let mut conn = db.acquire().await?;
    sessions::touch_auth_time(&mut conn, session_id, auth_time).await?;

    Ok(IssuedStepUp {
        access_token: token_issuer.issue(user_id, session_id, auth_time, &jkt),
        auth_time,
    })
}
