use crate::db::registrations::ReserveError;
use crate::db::{registrations, users};
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
    pub request: RegistrationRequest,
}

pub struct Started {
    pub registration_id: RegistrationId,
    pub response: RegistrationResponse,
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
        request,
    } = input;

    if !is_random_uuid(user_id) {
        return Err(StartError::InvalidUserId);
    }

    // TODO: check the invite here once invites exist, so only invite holders can probe usernames.

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

    let response = server::registration_start(setup, request, user_id.as_bytes())?;
    tx.commit().await?;

    Ok(Started {
        registration_id,
        response,
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
}
