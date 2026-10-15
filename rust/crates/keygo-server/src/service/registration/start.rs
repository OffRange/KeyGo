use crate::db::registrations::{self, ReserveError};
use crate::db::users;
use crate::domain::{RegistrationId, Username};
use crate::service::registration::TTL;
use keygo_opaque::server::ServerSetup;
use keygo_opaque::{OpaqueError, RegistrationRequest, RegistrationResponse, server};
use sqlx::PgPool;
use std::time::Duration;
use thiserror::Error;
use uuid::{Uuid, Variant};

#[derive(Debug, Error)]
pub enum StartError {
    #[error("user_id must be a random (version 4) UUID")]
    InvalidUserId,
    #[error("username is already taken")]
    UsernameTaken,
    #[error("user_id is already registered")]
    UserIdTaken,
    #[error(transparent)]
    Opaque(#[from] OpaqueError),
    #[error("database error: {0}")]
    Db(#[from] sqlx::Error),
}

impl From<ReserveError> for StartError {
    fn from(err: ReserveError) -> Self {
        match err {
            ReserveError::UsernameTaken => Self::UsernameTaken,
            ReserveError::UserIdTaken => Self::UserIdTaken,
            ReserveError::Db(err) => Self::Db(err),
        }
    }
}

pub struct StartRegistration {
    pub username: Username,
    pub user_id: Uuid,
    // TODO: add `invite: Option<InviteHash>` once invites exist.
    pub registration_request: RegistrationRequest,
}

pub struct Started {
    pub registration_id: RegistrationId,
    pub registration_response: RegistrationResponse,
    pub expires_in: Duration,
}

pub async fn start(
    db: &PgPool,
    setup: &ServerSetup,
    input: StartRegistration,
) -> Result<Started, StartError> {
    let StartRegistration {
        username,
        user_id,
        registration_request,
    } = input;

    if !is_random_uuid(user_id) {
        return Err(StartError::InvalidUserId);
    }

    // TODO: check the invite here once invites exist, so only invite holders can probe usernames.

    // Pure computation, so it runs before the transaction to keep the reservation's lock short.
    let registration_response =
        server::registration_start(setup, registration_request, user_id.as_bytes())?;

    let mut tx = db.begin().await?;
    let registration_id = RegistrationId::random();
    registrations::reserve(&mut tx, &registration_id, user_id, &username, TTL).await?;

    // Checked after reserving on purpose. A finish that consumed the previous reservation for
    // this name or id still holds its row (or index entry), so reserve waits for it to commit,
    // and these reads, each with a fresh snapshot, then see the user it created.
    if users::username_exists(&mut tx, &username).await? {
        return Err(StartError::UsernameTaken);
    }

    if users::id_exists(&mut tx, user_id).await? {
        return Err(StartError::UserIdTaken);
    }

    tx.commit().await?;

    Ok(Started {
        registration_id,
        registration_response,
        expires_in: TTL,
    })
}

fn is_random_uuid(id: Uuid) -> bool {
    // The nil UUID has version 0, so the version check already rejects it.
    id.get_version_num() == 4 && id.get_variant() == Variant::RFC4122
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{count, register, username};
    use keygo_opaque::client::ClientRegistration;

    #[test]
    fn accepts_random_uuids() {
        assert!(is_random_uuid(Uuid::new_v4()));
        assert!(is_random_uuid(
            "7f0c1e52-3a9b-4c11-8d0e-2b5a6c9d4e10".parse().unwrap()
        ));
    }

    #[test]
    fn rejects_structured_uuids() {
        assert!(!is_random_uuid(Uuid::nil()));
        assert!(!is_random_uuid(Uuid::max()));
        // Version 1 (time-based) and version 4 bits with the Microsoft variant.
        assert!(!is_random_uuid(
            "7f0c1e52-3a9b-1c11-8d0e-2b5a6c9d4e10".parse().unwrap()
        ));
        assert!(!is_random_uuid(
            "7f0c1e52-3a9b-4c11-cd0e-2b5a6c9d4e10".parse().unwrap()
        ));
    }

    async fn start_as(db: &PgPool, user_id: Uuid, name: &str) -> Result<Started, StartError> {
        let (_, registration_request) = ClientRegistration::start(b"password").unwrap();
        let input = StartRegistration {
            username: username(name),
            user_id,
            registration_request,
        };
        start(db, &ServerSetup::generate(), input).await
    }

    #[sqlx::test]
    async fn start_reserves_the_username(db: PgPool) {
        let started = start_as(&db, Uuid::new_v4(), "ada").await.unwrap();

        assert_eq!(started.expires_in, TTL);
        assert_eq!(count(&db, "pending_registrations").await, 1);
        let err = start_as(&db, Uuid::new_v4(), "ada").await.err().unwrap();
        assert!(matches!(err, StartError::UsernameTaken), "{err:?}");
    }

    #[sqlx::test]
    async fn start_rejects_structured_user_ids(db: PgPool) {
        let err = start_as(&db, Uuid::nil(), "ada").await.err().unwrap();

        assert!(matches!(err, StartError::InvalidUserId), "{err:?}");
        assert_eq!(count(&db, "pending_registrations").await, 0);
    }

    #[sqlx::test]
    async fn start_rejects_a_registered_username(db: PgPool) {
        register(&db, &ServerSetup::generate(), "ada").await;

        let err = start_as(&db, Uuid::new_v4(), "ada").await.err().unwrap();

        assert!(matches!(err, StartError::UsernameTaken), "{err:?}");
        assert_eq!(count(&db, "pending_registrations").await, 0);
    }

    #[sqlx::test]
    async fn start_rejects_a_registered_user_id(db: PgPool) {
        let user_id = register(&db, &ServerSetup::generate(), "ada").await;

        let err = start_as(&db, user_id, "bob").await.err().unwrap();

        assert!(matches!(err, StartError::UserIdTaken), "{err:?}");
        assert_eq!(count(&db, "pending_registrations").await, 0);
    }

    #[sqlx::test]
    async fn restart_with_the_same_user_id_replaces_the_reservation(db: PgPool) {
        let user_id = Uuid::new_v4();
        start_as(&db, user_id, "ada").await.unwrap();

        start_as(&db, user_id, "bob").await.unwrap();

        assert_eq!(count(&db, "pending_registrations").await, 1);
        start_as(&db, Uuid::new_v4(), "ada").await.unwrap();
    }
}
