use crate::db::{opaque_records, registrations, users};
use crate::domain::{RegistrationId, Username};
use keygo_opaque::{RegistrationUpload, server};
use sqlx::PgPool;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum FinishError {
    #[error("registration expired or unknown")]
    Expired,
    #[error("database error: {0}")]
    Db(#[from] sqlx::Error),
}

pub struct FinishRegistration {
    pub registration_id: RegistrationId,
    pub registration_upload: RegistrationUpload,
    // TODO: key bundle + device info
}

pub struct Finished {
    pub username: Username,
    // TODO: session
}

pub async fn finish(db: &PgPool, input: FinishRegistration) -> Result<Finished, FinishError> {
    let FinishRegistration {
        registration_id,
        registration_upload,
    } = input;

    // Pure computation, so it runs before the transaction to keep the reservation's lock short.
    let password_file = server::registration_finish(registration_upload);

    let mut tx = db.begin().await?;
    let pending = registrations::take(&mut tx, &registration_id)
        .await?
        .ok_or(FinishError::Expired)?;
    users::insert(&mut tx, pending.user_id, &pending.username).await?;
    opaque_records::insert(&mut tx, pending.user_id, &password_file).await?;
    tx.commit().await?;

    Ok(Finished {
        username: pending.username,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::service::registration::{self, StartRegistration, Started};
    use crate::testing::{PASSWORD, count, expire, username};
    use keygo_opaque::client::ClientRegistration;
    use keygo_opaque::server::ServerSetup;
    use uuid::Uuid;

    async fn started(db: &PgPool, user_id: Uuid, name: &str) -> FinishRegistration {
        let (client, registration_request) = ClientRegistration::start(PASSWORD).unwrap();
        let input = StartRegistration {
            username: username(name),
            user_id,
            registration_request,
        };
        let Started {
            registration_id,
            registration_response,
            ..
        } = registration::start(db, &ServerSetup::generate(), input)
            .await
            .unwrap();
        let finish = client.finish(PASSWORD, registration_response).unwrap();

        FinishRegistration {
            registration_id,
            registration_upload: finish.upload,
        }
    }

    async fn user_count(db: &PgPool) -> (i64, i64, i64) {
        (
            count(db, "users").await,
            count(db, "opaque_records").await,
            count(db, "pending_registrations").await,
        )
    }

    #[sqlx::test]
    async fn finish_creates_the_user_and_record(db: PgPool) {
        let user_id = Uuid::new_v4();
        let input = started(&db, user_id, "Ada").await;

        let finished = finish(&db, input).await.unwrap();

        assert_eq!(finished.username, username("ada"));
        assert_eq!(user_count(&db).await, (1, 1, 0));
        let stored: (Uuid, String) = sqlx::query_as("select id, username from users")
            .fetch_one(&db)
            .await
            .unwrap();
        assert_eq!(stored, (user_id, "ada".to_owned()));
    }

    #[sqlx::test]
    async fn finish_consumes_the_registration(db: PgPool) {
        let input = started(&db, Uuid::new_v4(), "ada").await;
        let replay = FinishRegistration {
            registration_id: input.registration_id,
            registration_upload: RegistrationUpload::from_bytes(
                &input.registration_upload.to_bytes(),
            )
            .unwrap(),
        };
        finish(&db, input).await.unwrap();

        let err = finish(&db, replay).await.err().unwrap();

        assert!(matches!(err, FinishError::Expired), "{err:?}");
        assert_eq!(user_count(&db).await, (1, 1, 0));
    }

    #[sqlx::test]
    async fn finish_rejects_an_unknown_registration(db: PgPool) {
        let input = started(&db, Uuid::new_v4(), "ada").await;
        let unknown = FinishRegistration {
            registration_id: RegistrationId::random(),
            ..input
        };

        let err = finish(&db, unknown).await.err().unwrap();

        assert!(matches!(err, FinishError::Expired), "{err:?}");
        assert_eq!(user_count(&db).await, (0, 0, 1));
    }

    #[sqlx::test]
    async fn finish_rejects_an_expired_registration(db: PgPool) {
        let input = started(&db, Uuid::new_v4(), "ada").await;
        expire(&db, "pending_registrations").await;

        let err = finish(&db, input).await.err().unwrap();

        assert!(matches!(err, FinishError::Expired), "{err:?}");
        assert_eq!(user_count(&db).await, (0, 0, 1));
    }

    #[sqlx::test]
    async fn failed_finish_keeps_the_reservation(db: PgPool) {
        let user_id = Uuid::new_v4();
        let input = started(&db, user_id, "ada").await;
        sqlx::query("insert into users (id, username) values ($1, 'squatter')")
            .bind(user_id)
            .execute(&db)
            .await
            .unwrap();

        let err = finish(&db, input).await.err().unwrap();

        assert!(matches!(err, FinishError::Db(_)), "{err:?}");
        assert_eq!(user_count(&db).await, (1, 0, 1));
    }
}
