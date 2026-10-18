use crate::db::registrations::{self, ReserveError};
use crate::db::{invites, users};
use crate::domain::{InviteCode, InviteHash, RegistrationId, Username};
use crate::service::registration::{Signup, TTL};
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
    #[error("this server needs an invite to register")]
    InviteRequired,
    #[error("invite is not valid or has expired")]
    InviteInvalid,
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
    pub invite: Option<String>,
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
    signup: &Signup,
    input: StartRegistration,
) -> Result<Started, StartError> {
    let StartRegistration {
        username,
        user_id,
        invite,
        registration_request,
    } = input;

    if !is_random_uuid(user_id) {
        return Err(StartError::InvalidUserId);
    }

    // Before any username check, so only invite holders can probe usernames.
    let invite_hash = check_invite(db, signup, invite.as_deref()).await?;

    // Pure computation, so it runs before the transaction to keep the reservation's lock short.
    let registration_response =
        server::registration_start(setup, registration_request, user_id.as_bytes())?;

    let mut tx = db.begin().await?;
    let registration_id = RegistrationId::random();
    registrations::reserve(
        &mut tx,
        &registration_id,
        user_id,
        &username,
        invite_hash.as_ref(),
        TTL,
    )
    .await?;

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

/// Checks without reserving: two holders of one code can both start, and finish decides who
/// spends it.
async fn check_invite(
    db: &PgPool,
    signup: &Signup,
    invite: Option<&str>,
) -> Result<Option<InviteHash>, StartError> {
    let Signup::InviteOnly(pepper) = signup else {
        return Ok(None);
    };

    let code: InviteCode = invite
        .ok_or(StartError::InviteRequired)?
        .parse()
        .map_err(|_| StartError::InviteInvalid)?;
    let hash = pepper.hash(&code);

    let mut conn = db.acquire().await?;
    if !invites::is_open(&mut conn, &hash).await? {
        return Err(StartError::InviteInvalid);
    }
    Ok(Some(hash))
}

fn is_random_uuid(id: Uuid) -> bool {
    // The nil UUID has version 0, so the version check already rejects it.
    id.get_version_num() == 4 && id.get_variant() == Variant::RFC4122
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{count, create_invite, invite_only, register, username};
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
        start_with(db, &Signup::Open, None, user_id, name).await
    }

    async fn start_with(
        db: &PgPool,
        signup: &Signup,
        invite: Option<&str>,
        user_id: Uuid,
        name: &str,
    ) -> Result<Started, StartError> {
        let (_, registration_request) = ClientRegistration::start(b"password").unwrap();
        let input = StartRegistration {
            username: username(name),
            user_id,
            invite: invite.map(str::to_owned),
            registration_request,
        };
        start(db, &ServerSetup::generate(), signup, input).await
    }

    async fn pending_invite(db: &PgPool) -> Option<Vec<u8>> {
        sqlx::query_scalar("select invite_hash from pending_registrations")
            .fetch_one(db)
            .await
            .unwrap()
    }

    #[sqlx::test]
    async fn invite_only_start_carries_the_invite_without_spending_it(db: PgPool) {
        let code = create_invite(&db).await;

        start_with(&db, &invite_only(), Some(&code), Uuid::new_v4(), "ada")
            .await
            .unwrap();

        assert!(pending_invite(&db).await.is_some());
        assert_eq!(count(&db, "invites").await, 1);
    }

    #[sqlx::test]
    async fn one_invite_can_start_two_registrations(db: PgPool) {
        let code = create_invite(&db).await;

        start_with(&db, &invite_only(), Some(&code), Uuid::new_v4(), "ada")
            .await
            .unwrap();
        start_with(&db, &invite_only(), Some(&code), Uuid::new_v4(), "bob")
            .await
            .unwrap();

        assert_eq!(count(&db, "pending_registrations").await, 2);
    }

    #[sqlx::test]
    async fn invite_only_start_requires_an_invite(db: PgPool) {
        let err = start_with(&db, &invite_only(), None, Uuid::new_v4(), "ada")
            .await
            .err()
            .unwrap();

        assert!(matches!(err, StartError::InviteRequired), "{err:?}");
        assert_eq!(count(&db, "pending_registrations").await, 0);
    }

    #[sqlx::test]
    async fn unknown_and_malformed_invites_are_invalid(db: PgPool) {
        create_invite(&db).await;

        for invite in ["7KQ2M9XD4HPA", "not a code"] {
            let err = start_with(&db, &invite_only(), Some(invite), Uuid::new_v4(), "ada")
                .await
                .err()
                .unwrap();

            assert!(
                matches!(err, StartError::InviteInvalid),
                "{invite}: {err:?}"
            );
        }
        assert_eq!(count(&db, "pending_registrations").await, 0);
    }

    #[sqlx::test]
    async fn invite_is_checked_before_the_username(db: PgPool) {
        register(&db, &ServerSetup::generate(), "ada").await;

        let err = start_with(&db, &invite_only(), None, Uuid::new_v4(), "ada")
            .await
            .err()
            .unwrap();

        assert!(matches!(err, StartError::InviteRequired), "{err:?}");
    }

    #[sqlx::test]
    async fn open_signup_ignores_the_invite(db: PgPool) {
        start_with(
            &db,
            &Signup::Open,
            Some("not a code"),
            Uuid::new_v4(),
            "ada",
        )
        .await
        .unwrap();

        assert_eq!(pending_invite(&db).await, None);
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
