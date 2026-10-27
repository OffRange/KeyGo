use crate::domain::SessionId;
use sqlx::PgConnection;
use std::time::Duration;

pub async fn insert(
    conn: &mut PgConnection,
    session_id: SessionId,
    token_hash: &[u8; 32],
    ttl: Duration,
) -> sqlx::Result<()> {
    sqlx::query!(
        "insert into refresh_tokens (token_hash, session_id, expires_at)
         values ($1, $2, now() + make_interval(secs => $3))",
        token_hash,
        session_id,
        ttl.as_secs_f64(),
    )
    .execute(conn)
    .await?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::sessions;
    use crate::testing::{JKT, count, expires_in_secs, insert_device};
    use sqlx::PgPool;

    const TTL: Duration = Duration::from_hours(24);
    const HASH: [u8; 32] = [9; 32];

    async fn insert_session(db: &PgPool) -> SessionId {
        let (user_id, device_id) = insert_device(db).await;
        let session_id = SessionId::new_v4();
        let mut conn = db.acquire().await.unwrap();
        sessions::insert(&mut conn, session_id, user_id, device_id, &JKT, 0, TTL)
            .await
            .unwrap();
        session_id
    }

    #[sqlx::test]
    async fn token_is_stored_unused_under_its_hash(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();

        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();

        let stored: (Vec<u8>, SessionId, bool) =
            sqlx::query_as("select token_hash, session_id, used_at is null from refresh_tokens")
                .fetch_one(&db)
                .await
                .unwrap();
        assert_eq!(stored, (HASH.to_vec(), session_id, true));
    }

    #[sqlx::test]
    async fn token_expires_after_ttl(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();

        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();

        let remaining = expires_in_secs(&db, "refresh_tokens").await;
        assert!(
            (TTL.as_secs_f64() - 5.0..=TTL.as_secs_f64()).contains(&remaining),
            "{remaining}"
        );
    }

    #[sqlx::test]
    async fn duplicate_hash_is_rejected(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();

        let err = insert(&mut conn, session_id, &HASH, TTL).await.unwrap_err();

        assert!(err.as_database_error().unwrap().is_unique_violation());
    }

    #[sqlx::test]
    async fn token_requires_a_session(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();

        let err = insert(&mut conn, SessionId::new_v4(), &HASH, TTL)
            .await
            .unwrap_err();

        assert!(err.as_database_error().unwrap().is_foreign_key_violation());
    }

    #[sqlx::test]
    async fn token_is_deleted_with_its_session(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();

        sqlx::query("delete from sessions where id = $1")
            .bind(session_id)
            .execute(&db)
            .await
            .unwrap();

        assert_eq!(count(&db, "refresh_tokens").await, 0);
    }
}
