use crate::db::login_states::{self, Purpose};
use crate::db::users;
use crate::domain::{LoginId, Username};
use keygo_opaque::{CredentialFinalization, OpaqueError};
use sqlx::PgPool;
use thiserror::Error;
use uuid::Uuid;

#[derive(Debug, Error)]
pub enum FinishError {
    #[error("login expired or unknown")]
    Expired,
    #[error(transparent)]
    Opaque(#[from] OpaqueError),
    #[error("database error: {0}")]
    Db(#[from] sqlx::Error),
}

pub struct FinishLogin {
    pub login_id: LoginId,
    pub credential_finalization: CredentialFinalization,
}

pub struct Finished {
    pub user_id: Uuid,
    pub username: Username,
    // TODO: session
}

/// The state is consumed before verifying, so a failed finish cannot be retried with the same
/// `login_id`: every password guess costs a fresh `start`.
pub async fn finish(db: &PgPool, input: FinishLogin) -> Result<Finished, FinishError> {
    let FinishLogin {
        login_id,
        credential_finalization,
    } = input;

    let mut conn = db.acquire().await?;
    let pending = login_states::take(&mut conn, &login_id, Purpose::Login)
        .await?
        .ok_or(FinishError::Expired)?;

    // TODO: derive the session from the session key once sessions exist.
    let _session_key = pending.state.finish(credential_finalization)?;

    // The fake path cannot produce a valid finalization, and an account deleted since `start` has
    // no user left to log into; both answer exactly like a wrong password.
    let user_id = pending.user_id.ok_or(OpaqueError::InvalidLogin)?;
    let username = users::find_username(&mut conn, user_id)
        .await?
        .ok_or(OpaqueError::InvalidLogin)?;

    Ok(Finished { user_id, username })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{PASSWORD, count, expire, finish_login, register, start_login, username};
    use keygo_opaque::server::ServerSetup;

    #[sqlx::test]
    async fn right_password_logs_in(db: PgPool) {
        let setup = ServerSetup::generate();
        let user_id = register(&db, &setup, "ada").await;
        let (client, started) = start_login(&db, &setup, "ADA").await;

        let finished = finish(&db, finish_login(client, started)).await.unwrap();

        assert_eq!(finished.user_id, user_id);
        assert_eq!(finished.username, username("ada"));
        assert_eq!(count(&db, "login_states").await, 0);
    }

    #[sqlx::test]
    async fn login_cannot_be_finished_twice(db: PgPool) {
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (client, started) = start_login(&db, &setup, "ada").await;
        let input = finish_login(client, started);
        let replay = FinishLogin {
            login_id: input.login_id,
            credential_finalization: CredentialFinalization::from_bytes(
                &input.credential_finalization.to_bytes(),
            )
            .unwrap(),
        };
        finish(&db, input).await.unwrap();

        let err = finish(&db, replay).await.err().unwrap();

        assert!(matches!(err, FinishError::Expired), "{err:?}");
    }

    #[sqlx::test]
    async fn foreign_finalization_is_invalid_and_consumes_the_login(db: PgPool) {
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (_, target) = start_login(&db, &setup, "ada").await;
        let (client, other) = start_login(&db, &setup, "ada").await;
        let forged = FinishLogin {
            login_id: target.login_id,
            ..finish_login(client, other)
        };
        let retry = FinishLogin {
            login_id: forged.login_id,
            credential_finalization: CredentialFinalization::from_bytes(
                &forged.credential_finalization.to_bytes(),
            )
            .unwrap(),
        };

        let err = finish(&db, forged).await.err().unwrap();
        let retried = finish(&db, retry).await.err().unwrap();

        assert!(
            matches!(err, FinishError::Opaque(OpaqueError::InvalidLogin)),
            "{err:?}"
        );
        assert!(matches!(retried, FinishError::Expired), "{retried:?}");
    }

    #[sqlx::test]
    async fn wrong_password_fails_at_the_client(db: PgPool) {
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (_, started) = start_login(&db, &setup, "ada").await;
        let (client, _) = keygo_opaque::client::ClientLogin::start(b"wrong password").unwrap();

        let err = client
            .finish(b"wrong password", started.credential_response)
            .err()
            .unwrap();

        assert_eq!(err, OpaqueError::InvalidLogin);
    }

    #[sqlx::test]
    async fn unknown_user_fails_at_the_client_like_a_wrong_password(db: PgPool) {
        let (client, started) = start_login(&db, &ServerSetup::generate(), "ada").await;

        let err = client
            .finish(PASSWORD, started.credential_response)
            .err()
            .unwrap();

        assert_eq!(err, OpaqueError::InvalidLogin);
    }

    #[sqlx::test]
    async fn unknown_user_rejects_any_finalization(db: PgPool) {
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (_, fake) = start_login(&db, &setup, "bob").await;
        let (client, real) = start_login(&db, &setup, "ada").await;
        let input = FinishLogin {
            login_id: fake.login_id,
            ..finish_login(client, real)
        };

        let err = finish(&db, input).await.err().unwrap();

        assert!(
            matches!(err, FinishError::Opaque(OpaqueError::InvalidLogin)),
            "{err:?}"
        );
    }

    #[sqlx::test]
    async fn deleted_user_cannot_finish(db: PgPool) {
        let setup = ServerSetup::generate();
        let user_id = register(&db, &setup, "ada").await;
        let (client, started) = start_login(&db, &setup, "ada").await;
        sqlx::query("delete from users where id = $1")
            .bind(user_id)
            .execute(&db)
            .await
            .unwrap();

        let err = finish(&db, finish_login(client, started))
            .await
            .err()
            .unwrap();

        assert!(
            matches!(err, FinishError::Opaque(OpaqueError::InvalidLogin)),
            "{err:?}"
        );
    }

    #[sqlx::test]
    async fn unknown_login_is_expired(db: PgPool) {
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (client, started) = start_login(&db, &setup, "ada").await;
        let input = FinishLogin {
            login_id: LoginId::random(),
            ..finish_login(client, started)
        };

        let err = finish(&db, input).await.err().unwrap();

        assert!(matches!(err, FinishError::Expired), "{err:?}");
        assert_eq!(count(&db, "login_states").await, 1);
    }

    #[sqlx::test]
    async fn expired_login_is_expired(db: PgPool) {
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (client, started) = start_login(&db, &setup, "ada").await;
        expire(&db, "login_states").await;

        let err = finish(&db, finish_login(client, started))
            .await
            .err()
            .unwrap();

        assert!(matches!(err, FinishError::Expired), "{err:?}");
    }
}
