use crate::domain::{DeviceId, RevokeReason, SessionId};
use sqlx::PgConnection;
use std::time::Duration;

pub async fn insert(
    conn: &mut PgConnection,
    session_id: SessionId,
    device_id: DeviceId,
    jkt: &[u8; 32],
    auth_time: u64,
    ttl: Duration,
) -> sqlx::Result<()> {
    sqlx::query!(
        "insert into sessions (id, device_id, dpop_jkt, auth_time, expires_at)
         values ($1, $2, $3, to_timestamp($4::int8), now() + make_interval(secs => $5))",
        session_id,
        device_id.as_uuid(),
        jkt,
        i64::try_from(auth_time).expect("auth_time fits a timestamptz"),
        ttl.as_secs_f64(),
    )
    .execute(conn)
    .await?;

    Ok(())
}

pub async fn revoke_by_device(
    conn: &mut PgConnection,
    device_id: DeviceId,
    reason: RevokeReason,
) -> sqlx::Result<()> {
    sqlx::query!(
        "update sessions
         set revoked_at = now(), revoke_reason = $1
         where device_id = $2 and revoked_at is null",
        reason.as_str(),
        device_id.as_uuid(),
    )
    .execute(conn)
    .await?;

    Ok(())
}

pub async fn revoke(
    conn: &mut PgConnection,
    id: SessionId,
    reason: RevokeReason,
) -> sqlx::Result<()> {
    sqlx::query!(
        "update sessions
         set revoked_at = now(), revoke_reason = $1
         where id = $2 and revoked_at is null",
        reason.as_str(),
        id,
    )
    .execute(conn)
    .await?;

    Ok(())
}

pub async fn delete_ended(conn: &mut PgConnection, retention: Duration) -> sqlx::Result<u64> {
    let result = sqlx::query!(
        "delete from sessions
         where least(revoked_at, expires_at) <= now() - make_interval(secs => $1)",
        retention.as_secs_f64(),
    )
    .execute(conn)
    .await?;

    Ok(result.rows_affected())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{JKT, count, expires_in_secs, insert_device};
    use sqlx::PgPool;
    use uuid::Uuid;

    const TTL: Duration = Duration::from_hours(24);

    const AUTH_TIME: u64 = 1_791_504_000;

    #[sqlx::test]
    async fn session_is_bound_to_device_and_key(db: PgPool) {
        let (_, device_id) = insert_device(&db).await;
        let session_id = SessionId::new_v4();
        let mut conn = db.acquire().await.unwrap();

        insert(&mut conn, session_id, device_id, &JKT, AUTH_TIME, TTL)
            .await
            .unwrap();

        let stored: (Uuid, Uuid, Vec<u8>, i64) = sqlx::query_as(
            "select id, device_id, dpop_jkt, extract(epoch from auth_time)::int8 from sessions",
        )
        .fetch_one(&db)
        .await
        .unwrap();
        assert_eq!(
            stored,
            (
                session_id,
                device_id.as_uuid(),
                JKT.to_vec(),
                AUTH_TIME as i64,
            )
        );
    }

    #[sqlx::test]
    async fn session_expires_after_ttl(db: PgPool) {
        let (_, device_id) = insert_device(&db).await;
        let mut conn = db.acquire().await.unwrap();

        insert(
            &mut conn,
            SessionId::new_v4(),
            device_id,
            &JKT,
            AUTH_TIME,
            TTL,
        )
        .await
        .unwrap();

        let remaining = expires_in_secs(&db, "sessions").await;
        assert!(
            (TTL.as_secs_f64() - 5.0..=TTL.as_secs_f64()).contains(&remaining),
            "{remaining}"
        );
    }

    #[sqlx::test]
    async fn session_requires_a_device(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();

        let err = insert(
            &mut conn,
            SessionId::new_v4(),
            DeviceId::random(),
            &JKT,
            AUTH_TIME,
            TTL,
        )
        .await
        .unwrap_err();

        assert!(err.as_database_error().unwrap().is_foreign_key_violation());
    }

    #[sqlx::test]
    async fn session_is_deleted_with_its_device(db: PgPool) {
        let (_, device_id) = insert_device(&db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(
            &mut conn,
            SessionId::new_v4(),
            device_id,
            &JKT,
            AUTH_TIME,
            TTL,
        )
        .await
        .unwrap();

        sqlx::query("delete from devices where id = $1")
            .bind(device_id.as_uuid())
            .execute(&db)
            .await
            .unwrap();

        assert_eq!(count(&db, "sessions").await, 0);
    }

    async fn insert_session(db: &PgPool) -> DeviceId {
        let (_, device_id) = insert_device(db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(
            &mut conn,
            SessionId::new_v4(),
            device_id,
            &JKT,
            AUTH_TIME,
            TTL,
        )
        .await
        .unwrap();
        device_id
    }

    #[sqlx::test]
    async fn revoke_records_the_reason(db: PgPool) {
        let device_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();

        revoke_by_device(&mut conn, device_id, RevokeReason::Replaced)
            .await
            .unwrap();

        let (reason, revoked): (String, bool) =
            sqlx::query_as("select revoke_reason, revoked_at is not null from sessions")
                .fetch_one(&db)
                .await
                .unwrap();
        assert_eq!((reason.as_str(), revoked), ("replaced", true));
    }

    #[sqlx::test]
    async fn revoke_leaves_revoked_sessions_alone(db: PgPool) {
        let device_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();
        sqlx::query(
            "update sessions set revoked_at = now() - interval '1 day', revoke_reason = 'logout'",
        )
        .execute(&db)
        .await
        .unwrap();

        revoke_by_device(&mut conn, device_id, RevokeReason::Replaced)
            .await
            .unwrap();

        let (reason, old): (String, bool) = sqlx::query_as(
            "select revoke_reason, revoked_at < now() - interval '1 hour' from sessions",
        )
        .fetch_one(&db)
        .await
        .unwrap();
        assert_eq!((reason.as_str(), old), ("logout", true));
    }

    const RETENTION: Duration = Duration::from_hours(30 * 24);

    async fn open_session(db: &PgPool, device_id: DeviceId) {
        let mut conn = db.acquire().await.unwrap();
        insert(
            &mut conn,
            SessionId::new_v4(),
            device_id,
            &JKT,
            AUTH_TIME,
            TTL,
        )
        .await
        .unwrap();
    }

    async fn end_live_session(db: &PgPool, column: &str, ago: &str) {
        sqlx::query(sqlx::AssertSqlSafe(format!(
            "update sessions set {column} = now() - interval '{ago}' where revoked_at is null"
        )))
        .execute(db)
        .await
        .unwrap();
    }

    #[sqlx::test]
    async fn delete_ended_removes_sessions_past_retention(db: PgPool) {
        let device_id = insert_session(&db).await;
        end_live_session(&db, "revoked_at", "31 days").await;
        open_session(&db, device_id).await;
        end_live_session(&db, "expires_at", "31 days").await;
        let mut conn = db.acquire().await.unwrap();

        assert_eq!(delete_ended(&mut conn, RETENTION).await.unwrap(), 2);
        assert_eq!(count(&db, "sessions").await, 0);
    }

    #[sqlx::test]
    async fn delete_ended_keeps_live_and_recently_ended_sessions(db: PgPool) {
        let device_id = insert_session(&db).await;
        end_live_session(&db, "revoked_at", "29 days").await;
        open_session(&db, device_id).await;
        let mut conn = db.acquire().await.unwrap();

        assert_eq!(delete_ended(&mut conn, RETENTION).await.unwrap(), 0);
        assert_eq!(count(&db, "sessions").await, 2);
    }
}
