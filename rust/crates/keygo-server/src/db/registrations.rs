use super::corrupt;
use crate::domain::{InviteHash, RegistrationId, Username};
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
    invite_hash: Option<&InviteHash>,
    ttl: Duration,
) -> Result<(), ReserveError> {
    sqlx::query!(
        "delete from pending_registrations
         where expires_at <= now() and (username = $1 or user_id = $2)",
        username.as_str(),
        user_id,
    )
    .execute(&mut *conn)
    .await?;

    // A concurrent reserve that wins between the sweep and here surfaces as a unique violation on
    // username, or as a takeover on user_id, so the two statements need no surrounding transaction.
    sqlx::query!(
        "insert into pending_registrations (id, user_id, username, invite_hash, expires_at)
         values ($1, $2, $3, $4, now() + make_interval(secs => $5))
         on conflict (user_id) do update
         set id = excluded.id, username = excluded.username,
             invite_hash = excluded.invite_hash, expires_at = excluded.expires_at",
        registration_id.as_bytes().as_slice(),
        user_id,
        username.as_str(),
        invite_hash.map(|hash| hash.as_bytes().as_slice()),
        ttl.as_secs_f64(),
    )
    .execute(conn)
    .await?;

    Ok(())
}

pub struct Pending {
    pub user_id: Uuid,
    pub username: Username,
    pub invite_hash: Option<InviteHash>,
}

/// Deletes the reservation as it reads it, so a registration can be finished at most once. Returns
/// `None` for an unknown or expired registration.
pub async fn take(
    conn: &mut PgConnection,
    registration_id: &RegistrationId,
) -> sqlx::Result<Option<Pending>> {
    let Some(row) = sqlx::query!(
        "delete from pending_registrations
         where id = $1 and expires_at > now()
         returning user_id, username, invite_hash",
        registration_id.as_bytes().as_slice(),
    )
    .fetch_optional(conn)
    .await?
    else {
        return Ok(None);
    };

    let username = Username::parse(&row.username).map_err(corrupt)?;
    let invite_hash = row
        .invite_hash
        .map(|hash| hash.as_slice().try_into())
        .transpose()
        .map_err(corrupt)?;

    Ok(Some(Pending {
        user_id: row.user_id,
        username,
        invite_hash,
    }))
}

pub async fn delete_expired(conn: &mut PgConnection) -> sqlx::Result<u64> {
    let result = sqlx::query!("delete from pending_registrations where expires_at <= now()")
        .execute(conn)
        .await?;

    Ok(result.rows_affected())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{count, expire, username};
    use sqlx::PgPool;

    const TTL: Duration = Duration::from_mins(5);

    async fn reserve_new(
        conn: &mut PgConnection,
        user_id: Uuid,
        name: &str,
    ) -> Result<RegistrationId, ReserveError> {
        let registration_id = RegistrationId::random();
        reserve(conn, &registration_id, user_id, &username(name), None, TTL).await?;
        Ok(registration_id)
    }

    #[sqlx::test]
    async fn reservation_is_taken_once(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let user_id = Uuid::new_v4();
        let registration_id = reserve_new(&mut conn, user_id, "ada").await.unwrap();

        let pending = take(&mut conn, &registration_id).await.unwrap().unwrap();

        assert_eq!(pending.user_id, user_id);
        assert_eq!(pending.username, username("ada"));
        assert_eq!(pending.invite_hash, None);
        assert!(take(&mut conn, &registration_id).await.unwrap().is_none());
        assert_eq!(count(&db, "pending_registrations").await, 0);
    }

    #[sqlx::test]
    async fn reservation_carries_its_invite(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let registration_id = RegistrationId::random();
        let invite = InviteHash::from([7; 32]);
        reserve(
            &mut conn,
            &registration_id,
            Uuid::new_v4(),
            &username("ada"),
            Some(&invite),
            TTL,
        )
        .await
        .unwrap();

        let pending = take(&mut conn, &registration_id).await.unwrap().unwrap();

        assert_eq!(pending.invite_hash, Some(invite));
    }

    #[sqlx::test]
    async fn unknown_registration_is_none(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        reserve_new(&mut conn, Uuid::new_v4(), "ada").await.unwrap();

        let pending = take(&mut conn, &RegistrationId::random()).await.unwrap();

        assert!(pending.is_none());
        assert_eq!(count(&db, "pending_registrations").await, 1);
    }

    #[sqlx::test]
    async fn expired_registration_is_none(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let registration_id = reserve_new(&mut conn, Uuid::new_v4(), "ada").await.unwrap();
        expire(&db, "pending_registrations").await;

        assert!(take(&mut conn, &registration_id).await.unwrap().is_none());
    }

    #[sqlx::test]
    async fn live_reservation_holds_its_username(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        reserve_new(&mut conn, Uuid::new_v4(), "ada").await.unwrap();

        let err = reserve_new(&mut conn, Uuid::new_v4(), "ada")
            .await
            .unwrap_err();

        assert!(matches!(err, ReserveError::UsernameTaken), "{err:?}");
    }

    #[sqlx::test]
    async fn expired_reservation_frees_its_username(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let stale = reserve_new(&mut conn, Uuid::new_v4(), "ada").await.unwrap();
        expire(&db, "pending_registrations").await;
        let user_id = Uuid::new_v4();

        let fresh = reserve_new(&mut conn, user_id, "ada").await.unwrap();

        assert!(take(&mut conn, &stale).await.unwrap().is_none());
        let pending = take(&mut conn, &fresh).await.unwrap().unwrap();
        assert_eq!(pending.user_id, user_id);
    }

    #[sqlx::test]
    async fn same_user_id_takes_over_its_reservation(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let user_id = Uuid::new_v4();
        let first = reserve_new(&mut conn, user_id, "ada").await.unwrap();

        let second = reserve_new(&mut conn, user_id, "bob").await.unwrap();

        assert!(take(&mut conn, &first).await.unwrap().is_none());
        let pending = take(&mut conn, &second).await.unwrap().unwrap();
        assert_eq!(pending.username, username("bob"));
        reserve_new(&mut conn, Uuid::new_v4(), "ada").await.unwrap();
    }

    #[sqlx::test]
    async fn takeover_cannot_claim_another_users_name(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let user_id = Uuid::new_v4();
        let own = reserve_new(&mut conn, user_id, "ada").await.unwrap();
        reserve_new(&mut conn, Uuid::new_v4(), "bob").await.unwrap();

        let err = reserve_new(&mut conn, user_id, "bob").await.unwrap_err();

        assert!(matches!(err, ReserveError::UsernameTaken), "{err:?}");
        assert!(take(&mut conn, &own).await.unwrap().is_some());
    }

    #[sqlx::test]
    async fn reservation_expires_after_ttl(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        reserve_new(&mut conn, Uuid::new_v4(), "ada").await.unwrap();

        let remaining = crate::testing::expires_in_secs(&db, "pending_registrations").await;

        assert!(
            (TTL.as_secs_f64() - 5.0..=TTL.as_secs_f64()).contains(&remaining),
            "{remaining}"
        );
    }

    #[sqlx::test]
    async fn delete_expired_keeps_live_reservations(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        reserve_new(&mut conn, Uuid::new_v4(), "ada").await.unwrap();
        expire(&db, "pending_registrations").await;
        let live = reserve_new(&mut conn, Uuid::new_v4(), "bob").await.unwrap();

        assert_eq!(delete_expired(&mut conn).await.unwrap(), 1);
        assert_eq!(count(&db, "pending_registrations").await, 1);
        assert!(take(&mut conn, &live).await.unwrap().is_some());
    }

    #[sqlx::test]
    async fn stored_invalid_username_is_corrupt(db: PgPool) {
        let registration_id = RegistrationId::random();
        sqlx::query(
            "insert into pending_registrations (id, user_id, username, expires_at)
             values ($1, $2, 'NOT VALID', now() + interval '1 minute')",
        )
        .bind(registration_id.as_bytes().as_slice())
        .bind(Uuid::new_v4())
        .execute(&db)
        .await
        .unwrap();
        let mut conn = db.acquire().await.unwrap();

        let err = take(&mut conn, &registration_id).await.err().unwrap();

        assert!(matches!(err, sqlx::Error::Decode(_)));
    }
}
