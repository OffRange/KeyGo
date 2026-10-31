use crate::cache::StepUpStates;
use crate::domain::{LoginId, SessionId, TokenIssuer, UserId};
use crate::service;
use crate::service::handshake::{self, Error};
use crate::service::session::Freshened;
use keygo_opaque::{CredentialFinalization, DpopBinding};
use sqlx::PgPool;
use thiserror::Error;

pub struct FinishStepUp {
    pub session_id: SessionId,
    pub user_id: UserId,
    pub login_id: LoginId,
    pub credential_finalization: CredentialFinalization,
    pub binding: DpopBinding,
    pub jkt: [u8; 32],
}

#[derive(Debug, Error)]
pub enum FinishError {
    #[error(transparent)]
    Handshake(#[from] Error),
    #[error("session ended")]
    SessionEnded,
}

pub async fn finish(
    db: &PgPool,
    step_up_states: &StepUpStates,
    token_issuer: &TokenIssuer,
    input: FinishStepUp,
) -> Result<Freshened, FinishError> {
    let FinishStepUp {
        session_id,
        user_id,
        login_id,
        credential_finalization,
        binding,
        jkt,
    } = input;

    let started_by = handshake::complete(
        step_up_states,
        &login_id,
        credential_finalization,
        &binding,
        &jkt,
    )?;
    if started_by != user_id {
        return Err(Error::Expired.into());
    }

    service::session::freshen(db, token_issuer, user_id, session_id, &jkt)
        .await
        .map_err(Error::from)?
        .ok_or(FinishError::SessionEnded)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::{devices, sessions};
    use crate::domain::{DeviceId, RevokeReason};
    use crate::service::step_up::{StartStepUp, start};
    use crate::testing::{JKT, PASSWORD, device_info, issue_session, register, token_issuer};
    use keygo_opaque::client::ClientLogin;
    use keygo_opaque::server::ServerSetup;

    async fn step_up_as(
        db: &PgPool,
        states: &StepUpStates,
        setup: &ServerSetup,
        started_by: UserId,
        finished_by: UserId,
        session_id: SessionId,
    ) -> Result<Freshened, FinishError> {
        let (client, credential_request) = ClientLogin::start(PASSWORD).unwrap();
        let input = StartStepUp {
            user_id: started_by,
            credential_request,
        };
        let started = start(db, states, setup, input).await.unwrap();
        let finished = client
            .finish(PASSWORD, started.credential_response)
            .unwrap();
        let input = FinishStepUp {
            session_id,
            user_id: finished_by,
            login_id: started.login_id,
            credential_finalization: finished.finalization,
            binding: finished.session_key.dpop_binding(&JKT),
            jkt: JKT,
        };

        finish(db, states, &token_issuer(), input).await
    }

    #[sqlx::test]
    async fn finish_rejects_a_step_up_started_by_another_user(db: PgPool) {
        let states = StepUpStates::new();
        let setup = ServerSetup::generate();
        let ada = register(&db, &setup, "ada").await;
        let bob = register(&db, &setup, "bob").await;

        let result = step_up_as(&db, &states, &setup, ada, bob, SessionId::random()).await;

        assert!(matches!(
            result,
            Err(FinishError::Handshake(Error::Expired))
        ));
    }

    #[sqlx::test]
    async fn finish_refuses_a_session_that_ended_meanwhile(db: PgPool) {
        let states = StepUpStates::new();
        let setup = ServerSetup::generate();
        let ada = register(&db, &setup, "ada").await;
        let device_id = DeviceId::random();
        let mut conn = db.acquire().await.unwrap();
        devices::insert(&mut conn, device_id, ada, &device_info())
            .await
            .unwrap();
        let issued = issue_session(&db, ada, device_id, &JKT).await;
        let session_id = token_issuer()
            .verify(issued.access_token.expose())
            .unwrap()
            .session_id();
        sessions::revoke(&mut conn, session_id, RevokeReason::Logout)
            .await
            .unwrap();

        let result = step_up_as(&db, &states, &setup, ada, ada, session_id).await;

        assert!(matches!(result, Err(FinishError::SessionEnded)));
    }
}
