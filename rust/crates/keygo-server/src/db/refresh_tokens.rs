use crate::db::corrupt;
use crate::db::sessions::revoke_reason;
use crate::domain::{DeviceId, RevokeReason, SessionId};
use sqlx::PgConnection;
use std::time::Duration;
use uuid::Uuid;

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

#[derive(Debug, PartialEq, Eq)]
pub enum TokenStatus {
    Revoked(Option<RevokeReason>),
    Expired,
    Rotated,
    Live,
}

pub struct Presented {
    pub session_id: SessionId,
    pub user_id: Uuid,
    pub device_id: DeviceId,
    pub dpop_jkt: [u8; 32],
    pub auth_time: u64,
    pub status: TokenStatus,
}

pub async fn find(conn: &mut PgConnection, hash: &[u8; 32]) -> sqlx::Result<Option<Presented>> {
    let Some(row) = sqlx::query!(
        r#"select rt.session_id,
           d.user_id,
           s.device_id,
           s.dpop_jkt,
           extract(epoch from s.auth_time)::int8             as "auth_time!",
           s.revoked_at is not null                          as "revoked!",
           s.revoke_reason,
           rt.rotated_at is not null                         as "rotated!",
           (rt.expires_at <= now() or s.expires_at <= now()) as "expired!"
           from refresh_tokens rt
           join sessions s on s.id = rt.session_id
           join devices d on d.id = s.device_id
           where rt.token_hash = $1
        "#,
        hash
    )
    .fetch_optional(conn)
    .await?
    else {
        return Ok(None);
    };

    let status = if row.revoked {
        let reason = revoke_reason(row.revoke_reason)?;
        TokenStatus::Revoked(reason)
    } else if row.rotated {
        TokenStatus::Rotated
    } else if row.expired {
        TokenStatus::Expired
    } else {
        TokenStatus::Live
    };

    Ok(Some(Presented {
        session_id: row.session_id,
        user_id: row.user_id,
        device_id: row.device_id.into(),
        dpop_jkt: row.dpop_jkt.as_slice().try_into().map_err(corrupt)?,
        auth_time: u64::try_from(row.auth_time).map_err(corrupt)?,
        status,
    }))
}

pub async fn rotate(
    conn: &mut PgConnection,
    old_hash: &[u8; 32],
    new_hash: &[u8; 32],
    ttl: Duration,
) -> sqlx::Result<Option<Duration>> {
    let record = sqlx::query!(
        r#"with claimed as (
            update refresh_tokens set rotated_at = now()
            where token_hash = $1 and rotated_at is null
            returning session_id
         )
         insert into refresh_tokens (token_hash, session_id, expires_at)
         select $2, s.id, least(now() + make_interval(secs => $3), s.expires_at)
         from claimed c
         join sessions s on s.id = c.session_id
         returning extract(epoch from expires_at - now())::int8 as "expires_in!""#,
        old_hash,
        new_hash,
        ttl.as_secs_f64(),
    )
    .fetch_optional(conn)
    .await?;

    record
        .map(|record| {
            u64::try_from(record.expires_in)
                .map(Duration::from_secs)
                .map_err(corrupt)
        })
        .transpose()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::sessions;
    use crate::testing::{JKT, count, expires_in_secs, insert_device};
    use sqlx::PgPool;

    const TTL: Duration = Duration::from_hours(24);
    const HASH: [u8; 32] = [9; 32];
    const CHILD: [u8; 32] = [10; 32];

    async fn insert_session(db: &PgPool) -> SessionId {
        let (_, device_id) = insert_device(db).await;
        let session_id = SessionId::new_v4();
        let mut conn = db.acquire().await.unwrap();
        sessions::insert(&mut conn, session_id, device_id, &JKT, 0, TTL)
            .await
            .unwrap();
        session_id
    }

    #[sqlx::test]
    async fn token_is_stored_unrotated_under_its_hash(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();

        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();

        let stored: (Vec<u8>, SessionId, bool) =
            sqlx::query_as("select token_hash, session_id, rotated_at is null from refresh_tokens")
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

    async fn status(db: &PgPool, hash: &[u8; 32]) -> TokenStatus {
        let mut conn = db.acquire().await.unwrap();
        find(&mut conn, hash).await.unwrap().unwrap().status
    }

    async fn set(db: &PgPool, sql: &str) {
        sqlx::query(sqlx::AssertSqlSafe(sql.to_owned()))
            .execute(db)
            .await
            .unwrap();
    }

    #[sqlx::test]
    async fn find_returns_the_session_of_a_live_token(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();

        let presented = find(&mut conn, &HASH).await.unwrap().unwrap();

        let (user_id, device_id): (Uuid, Uuid) = sqlx::query_as("select user_id, id from devices")
            .fetch_one(&db)
            .await
            .unwrap();
        assert_eq!(presented.session_id, session_id);
        assert_eq!(presented.user_id, user_id);
        assert_eq!(presented.device_id.as_uuid(), device_id);
        assert_eq!(presented.dpop_jkt, JKT);
        assert_eq!(presented.status, TokenStatus::Live);
    }

    #[sqlx::test]
    async fn find_of_an_unknown_token_is_none(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();

        assert!(find(&mut conn, &HASH).await.unwrap().is_none());
    }

    #[sqlx::test]
    async fn revocation_decides_before_anything_else(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();
        rotate(&mut conn, &HASH, &CHILD, TTL).await.unwrap();
        sessions::revoke(&mut conn, session_id, RevokeReason::Replaced)
            .await
            .unwrap();
        set(
            &db,
            "update sessions set expires_at = now() - interval '1 second'",
        )
        .await;

        assert_eq!(
            status(&db, &HASH).await,
            TokenStatus::Revoked(Some(RevokeReason::Replaced)),
        );
    }

    #[sqlx::test]
    async fn an_expired_session_expires_its_live_token(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();
        rotate(&mut conn, &HASH, &CHILD, TTL).await.unwrap();
        set(
            &db,
            "update sessions set expires_at = now() - interval '1 second'",
        )
        .await;

        assert_eq!(status(&db, &HASH).await, TokenStatus::Rotated);
        assert_eq!(status(&db, &CHILD).await, TokenStatus::Expired);
    }

    #[sqlx::test]
    async fn a_rotated_token_stays_rotated_after_it_expires(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();
        rotate(&mut conn, &HASH, &CHILD, TTL).await.unwrap();
        set(
            &db,
            "update refresh_tokens set expires_at = now() - interval '1 second'",
        )
        .await;

        assert_eq!(status(&db, &HASH).await, TokenStatus::Rotated);
        assert_eq!(status(&db, &CHILD).await, TokenStatus::Expired);
    }

    #[sqlx::test]
    async fn stored_unknown_revoke_reason_is_corrupt(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();
        set(
            &db,
            "update sessions set revoked_at = now(), revoke_reason = 'nope'",
        )
        .await;

        let err = find(&mut conn, &HASH).await.err().unwrap();

        assert!(matches!(err, sqlx::Error::Decode(_)));
    }

    #[sqlx::test]
    async fn rotate_marks_the_token_and_stores_its_child(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();

        let expires_in = rotate(&mut conn, &HASH, &CHILD, TTL)
            .await
            .unwrap()
            .unwrap();

        assert!(TTL - expires_in <= Duration::from_secs(5), "{expires_in:?}");
        assert_eq!(status(&db, &HASH).await, TokenStatus::Rotated);
        assert_eq!(status(&db, &CHILD).await, TokenStatus::Live);
        let child_session: SessionId =
            sqlx::query_scalar("select session_id from refresh_tokens where token_hash = $1")
                .bind(CHILD.as_slice())
                .fetch_one(&db)
                .await
                .unwrap();
        assert_eq!(child_session, session_id);
    }

    #[sqlx::test]
    async fn rotate_caps_the_child_at_the_session_expiry(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();
        set(
            &db,
            "update sessions set expires_at = now() + interval '1 hour'",
        )
        .await;

        let expires_in = rotate(&mut conn, &HASH, &CHILD, TTL)
            .await
            .unwrap()
            .unwrap();

        assert_eq!(expires_in, Duration::from_hours(1));
    }

    #[sqlx::test]
    async fn rotate_claims_a_token_only_once(db: PgPool) {
        let session_id = insert_session(&db).await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, session_id, &HASH, TTL).await.unwrap();
        rotate(&mut conn, &HASH, &CHILD, TTL).await.unwrap();

        let second = rotate(&mut conn, &HASH, &[11; 32], TTL).await.unwrap();

        assert!(second.is_none());
        assert_eq!(count(&db, "refresh_tokens").await, 2);
    }
}
