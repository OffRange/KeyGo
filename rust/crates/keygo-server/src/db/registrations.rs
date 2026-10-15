use super::corrupt;
use crate::domain::{RegistrationId, Username};
use sqlx::PgConnection;
use std::time::Duration;
use thiserror::Error;
use uuid::Uuid;

#[derive(Debug, Error)]
pub enum ReserveError {
    #[error("user_id is already in use")]
    UserIdTaken,
    #[error("username is already in use")]
    UsernameTaken,
    #[error("database error: {0}")]
    Db(#[source] sqlx::Error),
}

impl From<sqlx::Error> for ReserveError {
    fn from(err: sqlx::Error) -> Self {
        let constraint = err
            .as_database_error()
            .filter(|db| db.is_unique_violation())
            .and_then(|db| db.constraint());

        match constraint {
            Some("pending_registrations_username_key") => Self::UsernameTaken,
            Some("pending_registrations_user_id_key") => Self::UserIdTaken,
            _ => Self::Db(err),
        }
    }
}

pub async fn reserve(
    conn: &mut PgConnection,
    registration_id: &RegistrationId,
    user_id: Uuid,
    username: &Username,
    ttl: Duration,
) -> Result<(), ReserveError> {
    sqlx::query!(
        "DELETE FROM pending_registrations
         WHERE expires_at <= now() AND (username = $1 OR user_id = $2)",
        username.as_str(),
        user_id,
    )
    .execute(&mut *conn)
    .await?;

    // A concurrent reserve that wins between the sweep and here surfaces as a unique violation on
    // username, or as a takeover on user_id, so the two statements need no surrounding transaction.
    sqlx::query!(
        "INSERT INTO pending_registrations (id, user_id, username, expires_at)
         VALUES ($1, $2, $3, now() + make_interval(secs => $4))
         ON CONFLICT (user_id) DO UPDATE
         SET id = excluded.id, username = excluded.username, expires_at = excluded.expires_at",
        registration_id.as_bytes().as_slice(),
        user_id,
        username.as_str(),
        ttl.as_secs_f64(),
    )
    .execute(conn)
    .await?;

    Ok(())
}

pub struct Pending {
    pub user_id: Uuid,
    pub username: Username,
}

/// Deletes the reservation as it reads it, so a registration can be finished at most once. Returns
/// `None` for an unknown or expired registration.
pub async fn take(
    conn: &mut PgConnection,
    registration_id: &RegistrationId,
) -> sqlx::Result<Option<Pending>> {
    let Some(row) = sqlx::query!(
        "DELETE FROM pending_registrations
         WHERE id = $1 AND expires_at > now()
         RETURNING user_id, username",
        registration_id.as_bytes().as_slice(),
    )
    .fetch_optional(conn)
    .await?
    else {
        return Ok(None);
    };

    let username = Username::parse(&row.username).map_err(corrupt)?;

    Ok(Some(Pending {
        user_id: row.user_id,
        username,
    }))
}
