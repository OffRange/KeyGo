use crate::cache::StepUpStates;
use crate::db::opaque_records;
use crate::domain::UserId;
use crate::service::login::{self, Error, Started};
use keygo_opaque::server::ServerSetup;
use keygo_opaque::{CredentialRequest, OpaqueError};
use sqlx::PgPool;

pub struct StartStepUp {
    pub user_id: UserId,
    pub credential_request: CredentialRequest,
}

pub async fn start(
    db: &PgPool,
    step_up_states: &StepUpStates,
    setup: &ServerSetup,
    input: StartStepUp,
) -> Result<Started, Error> {
    let StartStepUp {
        user_id,
        credential_request,
    } = input;

    let mut conn = db.acquire().await?;
    // Unlike the login/start endpoint, there is no fake path here as the caller is already authenticated
    let password_file = opaque_records::find_by_user_id(&mut conn, user_id)
        .await?
        .ok_or(OpaqueError::InvalidLogin)?;

    login::respond(
        step_up_states,
        setup,
        user_id,
        Some(password_file),
        user_id.as_uuid().as_bytes(),
        credential_request,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{PASSWORD, register};
    use keygo_opaque::client::ClientLogin;

    async fn start_step_up(
        db: &PgPool,
        states: &StepUpStates,
        setup: &ServerSetup,
        user_id: UserId,
    ) -> (ClientLogin, Result<Started, Error>) {
        let (client, credential_request) = ClientLogin::start(PASSWORD).unwrap();
        let input = StartStepUp {
            user_id,
            credential_request,
        };
        (client, start(db, states, setup, input).await)
    }

    #[sqlx::test]
    async fn start_stores_state_for_the_user(db: PgPool) {
        let states = StepUpStates::new();
        let setup = ServerSetup::generate();
        let user_id = register(&db, &setup, "ada").await;

        let (_, started) = start_step_up(&db, &states, &setup, user_id).await;
        let started = started.unwrap();

        assert_eq!(started.expires_in, states.ttl());
        let pending = states.take(&started.login_id).unwrap();
        assert_eq!(pending.user_id, user_id);
    }

    #[sqlx::test]
    async fn start_answers_with_the_registered_record(db: PgPool) {
        let states = StepUpStates::new();
        let setup = ServerSetup::generate();
        let user_id = register(&db, &setup, "ada").await;

        let (client, started) = start_step_up(&db, &states, &setup, user_id).await;

        assert!(
            client
                .finish(PASSWORD, started.unwrap().credential_response)
                .is_ok()
        );
    }

    #[sqlx::test]
    async fn start_refuses_a_user_without_a_record(db: PgPool) {
        let states = StepUpStates::new();

        let (_, started) =
            start_step_up(&db, &states, &ServerSetup::generate(), UserId::random()).await;

        assert!(matches!(
            started,
            Err(Error::Opaque(OpaqueError::InvalidLogin))
        ));
        assert_eq!(states.count(), 0);
    }
}
