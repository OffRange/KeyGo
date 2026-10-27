use crate::db::{refresh_tokens, sessions};
use crate::domain::{
    ACCESS_TOKEN_LIFETIME, AccessToken, DeviceId, RefreshToken, SessionId, TokenIssuer, unix_now,
};
use sqlx::{Connection, PgConnection};
use std::time::Duration;
use uuid::Uuid;

pub const SESSION_LIFETIME: Duration = Duration::from_hours(180 * 24);
pub const REFRESH_TTL: Duration = Duration::from_hours(30 * 24);

pub struct Issued {
    pub device_id: DeviceId,
    pub access_token: AccessToken,
    pub access_expires_in: Duration,
    pub refresh_token: RefreshToken,
    pub refresh_expires_in: Duration,
}

pub async fn issue(
    conn: &mut PgConnection,
    token_issuer: &TokenIssuer,
    user_id: Uuid,
    device_id: DeviceId,
    jkt: &[u8; 32],
) -> sqlx::Result<Issued> {
    let auth_time = unix_now();

    let mut tx = conn.begin().await?;
    let session_id = SessionId::new_v4();
    sessions::insert(
        &mut tx,
        session_id,
        user_id,
        device_id,
        jkt,
        auth_time,
        SESSION_LIFETIME,
    )
    .await?;

    let refresh_token = RefreshToken::generate();
    refresh_tokens::insert(&mut tx, session_id, &refresh_token.digest(), REFRESH_TTL).await?;
    tx.commit().await?;

    Ok(Issued {
        device_id,
        access_token: token_issuer.issue(user_id, session_id, auth_time, jkt),
        access_expires_in: ACCESS_TOKEN_LIFETIME,
        refresh_token,
        refresh_expires_in: REFRESH_TTL,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::RevokeReason;
    use crate::testing::{JKT, count, expires_in_secs, insert_device, token_issuer};
    use sqlx::PgPool;

    fn assert_expires_after(remaining: f64, ttl: Duration) {
        assert!(
            (ttl.as_secs_f64() - 5.0..=ttl.as_secs_f64()).contains(&remaining),
            "{remaining}"
        );
    }

    #[sqlx::test]
    async fn issue_binds_a_session_to_the_device_and_key(db: PgPool) {
        let (user_id, device_id) = insert_device(&db).await;
        let mut conn = db.acquire().await.unwrap();

        let issued = issue(&mut conn, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();

        assert_eq!(issued.device_id, device_id);
        assert_eq!(issued.refresh_expires_in, REFRESH_TTL);
        let stored: (Uuid, Uuid, Vec<u8>) =
            sqlx::query_as("select device_id, user_id, dpop_jkt from sessions")
                .fetch_one(&db)
                .await
                .unwrap();
        assert_eq!(stored, (device_id.as_uuid(), user_id, JKT.to_vec()));
        assert_expires_after(expires_in_secs(&db, "sessions").await, SESSION_LIFETIME);
    }

    #[sqlx::test]
    async fn issue_signs_an_access_token_for_the_stored_session(db: PgPool) {
        let (user_id, device_id) = insert_device(&db).await;
        let mut conn = db.acquire().await.unwrap();

        let issued = issue(&mut conn, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();

        let claims = token_issuer().verify(issued.access_token.expose()).unwrap();
        let (session_id, auth_time): (Uuid, i64) =
            sqlx::query_as("select id, extract(epoch from auth_time)::int8 from sessions")
                .fetch_one(&db)
                .await
                .unwrap();
        assert_eq!(claims.user_id(), user_id);
        assert_eq!(claims.session_id(), session_id);
        assert_eq!(claims.jkt(), &JKT);
        assert_eq!(claims.auth_time(), auth_time as u64);
        assert!(claims.is_fresh());
        assert_eq!(issued.access_expires_in, ACCESS_TOKEN_LIFETIME);
    }

    #[sqlx::test]
    async fn issue_stores_only_the_refresh_token_hash(db: PgPool) {
        let (user_id, device_id) = insert_device(&db).await;
        let mut conn = db.acquire().await.unwrap();

        let issued = issue(&mut conn, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();

        let stored: Vec<u8> = sqlx::query_scalar("select token_hash from refresh_tokens")
            .fetch_one(&db)
            .await
            .unwrap();
        assert_eq!(stored, issued.refresh_token.digest());
        assert_expires_after(expires_in_secs(&db, "refresh_tokens").await, REFRESH_TTL);
    }

    #[sqlx::test]
    async fn each_issue_opens_its_own_session(db: PgPool) {
        let (user_id, device_id) = insert_device(&db).await;
        let mut conn = db.acquire().await.unwrap();

        let first = issue(&mut conn, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();
        sessions::revoke(&mut conn, device_id, RevokeReason::Replaced)
            .await
            .unwrap();
        let second = issue(&mut conn, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();

        assert_ne!(first.refresh_token.digest(), second.refresh_token.digest());
        assert_eq!(count(&db, "sessions").await, 2);
        assert_eq!(count(&db, "refresh_tokens").await, 2);
    }

    #[sqlx::test]
    async fn a_device_has_at_most_one_live_session(db: PgPool) {
        let (user_id, device_id) = insert_device(&db).await;
        let mut conn = db.acquire().await.unwrap();
        issue(&mut conn, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();

        let err = issue(&mut conn, &token_issuer(), user_id, device_id, &JKT)
            .await
            .err()
            .unwrap();

        assert!(err.as_database_error().unwrap().is_unique_violation());
        assert_eq!(count(&db, "sessions").await, 1);
        assert_eq!(count(&db, "refresh_tokens").await, 1);
    }

    #[sqlx::test]
    async fn issue_rolls_back_with_the_outer_transaction(db: PgPool) {
        let (user_id, device_id) = insert_device(&db).await;
        let mut tx = db.begin().await.unwrap();

        issue(&mut tx, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();
        tx.rollback().await.unwrap();

        assert_eq!(count(&db, "sessions").await, 0);
        assert_eq!(count(&db, "refresh_tokens").await, 0);
    }

    #[sqlx::test]
    async fn issue_for_an_unknown_device_stores_nothing(db: PgPool) {
        let (user_id, _) = insert_device(&db).await;
        let mut conn = db.acquire().await.unwrap();

        let err = issue(
            &mut conn,
            &token_issuer(),
            user_id,
            DeviceId::random(),
            &JKT,
        )
        .await
        .err()
        .unwrap();

        assert!(err.as_database_error().unwrap().is_foreign_key_violation());
        assert_eq!(count(&db, "sessions").await, 0);
        assert_eq!(count(&db, "refresh_tokens").await, 0);
    }
}
