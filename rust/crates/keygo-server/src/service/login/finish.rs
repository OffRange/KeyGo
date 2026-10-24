use crate::cache::LoginStates;
use crate::db::users;
use crate::domain::{LoginId, Username};
use crate::service::login::Error;
use keygo_opaque::{CredentialFinalization, DpopBinding, OpaqueError};
use sqlx::PgPool;
use uuid::Uuid;

pub struct FinishLogin {
    pub login_id: LoginId,
    pub credential_finalization: CredentialFinalization,
    pub binding: DpopBinding,
    pub jkt: [u8; 32],
}

pub struct Finished {
    pub user_id: Uuid,
    pub username: Username,
    // TODO: session
}

/// The state is consumed before verifying, so a failed finish cannot be retried with the same
/// `login_id`: every password guess costs a fresh `start`.
pub async fn finish(
    db: &PgPool,
    login_states: &LoginStates,
    input: FinishLogin,
) -> Result<Finished, Error> {
    let FinishLogin {
        login_id,
        credential_finalization,
        binding,
        jkt,
    } = input;

    let pending = login_states.take(&login_id).ok_or(Error::Expired)?;
    let session_key = pending.state.finish(credential_finalization)?;
    session_key.verify_dpop_binding(&jkt, &binding)?;

    // The fake path cannot produce a valid finalization, and an account deleted since `start` has
    // no user left to log into; both answer exactly like a wrong password.
    let user_id = pending.user_id.ok_or(OpaqueError::InvalidLogin)?;

    let mut conn = db.acquire().await?;
    let username = users::find_username(&mut conn, user_id)
        .await?
        .ok_or(OpaqueError::InvalidLogin)?;

    Ok(Finished { user_id, username })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{PASSWORD, finish_login, register, start_login, username};
    use keygo_opaque::server::ServerSetup;
    use std::time::Duration;

    fn copy(input: &FinishLogin) -> FinishLogin {
        FinishLogin {
            login_id: input.login_id,
            credential_finalization: CredentialFinalization::from_bytes(
                &input.credential_finalization.to_bytes(),
            )
            .unwrap(),
            binding: DpopBinding::from_bytes(&input.binding.to_bytes()).unwrap(),
            jkt: input.jkt,
        }
    }

    #[sqlx::test]
    async fn right_password_logs_in(db: PgPool) {
        let login_states = LoginStates::new();
        let setup = ServerSetup::generate();
        let user_id = register(&db, &setup, "ada").await;
        let (client, started) = start_login(&db, &login_states, &setup, "ADA").await;

        let finished = finish(&db, &login_states, finish_login(client, started))
            .await
            .unwrap();

        assert_eq!(finished.user_id, user_id);
        assert_eq!(finished.username, username("ada"));
        assert_eq!(login_states.count(), 0);
    }

    #[sqlx::test]
    async fn login_cannot_be_finished_twice(db: PgPool) {
        let login_states = LoginStates::new();
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (client, started) = start_login(&db, &login_states, &setup, "ada").await;
        let input = finish_login(client, started);
        let replay = copy(&input);
        finish(&db, &login_states, input).await.unwrap();

        let err = finish(&db, &login_states, replay).await.err().unwrap();

        assert!(matches!(err, Error::Expired), "{err:?}");
    }

    #[sqlx::test]
    async fn foreign_finalization_is_invalid_and_consumes_the_login(db: PgPool) {
        let login_states = LoginStates::new();
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (_, target) = start_login(&db, &login_states, &setup, "ada").await;
        let (client, other) = start_login(&db, &login_states, &setup, "ada").await;
        let forged = FinishLogin {
            login_id: target.login_id,
            ..finish_login(client, other)
        };
        let retry = copy(&forged);

        let err = finish(&db, &login_states, forged).await.err().unwrap();
        let retried = finish(&db, &login_states, retry).await.err().unwrap();

        assert!(
            matches!(err, Error::Opaque(OpaqueError::InvalidLogin)),
            "{err:?}"
        );
        assert!(matches!(retried, Error::Expired), "{retried:?}");
    }

    #[sqlx::test]
    async fn binding_for_another_key_is_invalid_and_consumes_the_login(db: PgPool) {
        let login_states = LoginStates::new();
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (client, started) = start_login(&db, &login_states, &setup, "ada").await;
        let input = FinishLogin {
            jkt: [0x41; 32],
            ..finish_login(client, started)
        };

        let err = finish(&db, &login_states, input).await.err().unwrap();

        assert!(
            matches!(err, Error::Opaque(OpaqueError::InvalidLogin)),
            "{err:?}"
        );
        assert_eq!(login_states.count(), 0);
    }

    #[sqlx::test]
    async fn wrong_password_fails_at_the_client(db: PgPool) {
        let login_states = LoginStates::new();
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (_, started) = start_login(&db, &login_states, &setup, "ada").await;
        let (client, _) = keygo_opaque::client::ClientLogin::start(b"wrong password").unwrap();

        let err = client
            .finish(b"wrong password", started.credential_response)
            .err()
            .unwrap();

        assert_eq!(err, OpaqueError::InvalidLogin);
    }

    #[sqlx::test]
    async fn unknown_user_fails_at_the_client_like_a_wrong_password(db: PgPool) {
        let login_states = LoginStates::new();
        let (client, started) =
            start_login(&db, &login_states, &ServerSetup::generate(), "ada").await;

        let err = client
            .finish(PASSWORD, started.credential_response)
            .err()
            .unwrap();

        assert_eq!(err, OpaqueError::InvalidLogin);
    }

    #[sqlx::test]
    async fn unknown_user_rejects_any_finalization(db: PgPool) {
        let login_states = LoginStates::new();
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (_, fake) = start_login(&db, &login_states, &setup, "bob").await;
        let (client, real) = start_login(&db, &login_states, &setup, "ada").await;
        let input = FinishLogin {
            login_id: fake.login_id,
            ..finish_login(client, real)
        };

        let err = finish(&db, &login_states, input).await.err().unwrap();

        assert!(
            matches!(err, Error::Opaque(OpaqueError::InvalidLogin)),
            "{err:?}"
        );
    }

    #[sqlx::test]
    async fn deleted_user_cannot_finish(db: PgPool) {
        let login_states = LoginStates::new();
        let setup = ServerSetup::generate();
        let user_id = register(&db, &setup, "ada").await;
        let (client, started) = start_login(&db, &login_states, &setup, "ada").await;
        sqlx::query("delete from users where id = $1")
            .bind(user_id)
            .execute(&db)
            .await
            .unwrap();

        let err = finish(&db, &login_states, finish_login(client, started))
            .await
            .err()
            .unwrap();

        assert!(
            matches!(err, Error::Opaque(OpaqueError::InvalidLogin)),
            "{err:?}"
        );
    }

    #[sqlx::test]
    async fn unknown_login_is_expired(db: PgPool) {
        let login_states = LoginStates::new();
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (client, started) = start_login(&db, &login_states, &setup, "ada").await;
        let input = FinishLogin {
            login_id: LoginId::random(),
            ..finish_login(client, started)
        };

        let err = finish(&db, &login_states, input).await.err().unwrap();

        assert!(matches!(err, Error::Expired), "{err:?}");
        assert_eq!(login_states.count(), 1);
    }

    #[sqlx::test]
    async fn expired_login_is_expired(db: PgPool) {
        let login_states = LoginStates::with_ttl(Duration::from_millis(10));
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (client, started) = start_login(&db, &login_states, &setup, "ada").await;
        tokio::time::sleep(Duration::from_millis(50)).await;

        let err = finish(&db, &login_states, finish_login(client, started))
            .await
            .err()
            .unwrap();

        assert!(matches!(err, Error::Expired), "{err:?}");
    }
}
