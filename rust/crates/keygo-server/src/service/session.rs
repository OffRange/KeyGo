use crate::db::refresh_tokens::TokenStatus;
use crate::db::{refresh_tokens, sessions};
use crate::domain::{
    AccessToken, DeviceId, REFRESH_TTL, RefreshToken, RevokeReason, SESSION_LIFETIME, SessionId,
    TokenIssuer, UserId, unix_now,
};
use sqlx::{PgPool, PgTransaction};
use std::time::Duration;
use thiserror::Error;

pub struct Issued {
    pub device_id: DeviceId,
    pub access_token: AccessToken,
    pub refresh_token: RefreshToken,
    pub refresh_expires_in: Duration,
}

pub async fn issue(
    tx: &mut PgTransaction<'_>,
    token_issuer: &TokenIssuer,
    user_id: UserId,
    device_id: DeviceId,
    jkt: &[u8; 32],
) -> sqlx::Result<Issued> {
    let auth_time = unix_now();

    let session_id = SessionId::random();
    sessions::insert(tx, session_id, device_id, jkt, auth_time, SESSION_LIFETIME).await?;

    let refresh_token = RefreshToken::generate();
    refresh_tokens::insert(tx, session_id, &refresh_token.digest(), REFRESH_TTL).await?;

    Ok(Issued {
        device_id,
        access_token: token_issuer.issue(user_id, session_id, auth_time, jkt),
        refresh_token,
        refresh_expires_in: REFRESH_TTL,
    })
}

pub async fn logout(db: &PgPool, session_id: SessionId) -> sqlx::Result<()> {
    let mut conn = db.acquire().await?;
    sessions::revoke(&mut conn, session_id, RevokeReason::Logout).await
}

#[derive(Debug, Error)]
pub enum RefreshError {
    #[error("session revoked")]
    Revoked(Option<RevokeReason>),
    #[error("refresh token expired")]
    Expired,
    #[error("proof key is not the session's key")]
    WrongKey,
    #[error("database error")]
    Db(#[from] sqlx::Error),
}

pub async fn refresh(
    db: &PgPool,
    token_issuer: &TokenIssuer,
    refresh_token: &RefreshToken,
    jkt: &[u8; 32],
) -> Result<Issued, RefreshError> {
    let mut tx = db.begin().await?;
    let digest = refresh_token.digest();
    let Some(presented) = refresh_tokens::find(&mut tx, &digest).await? else {
        return Err(RefreshError::Revoked(None));
    };

    match presented.status {
        TokenStatus::Revoked(reason) => return Err(RefreshError::Revoked(reason)),
        TokenStatus::Expired => return Err(RefreshError::Expired),
        TokenStatus::Rotated => return reuse_detected(tx, presented.session_id).await,
        TokenStatus::Live => {}
    }

    // Checked only for a live token: a rotated one revokes its session whichever key presents it.
    if presented.dpop_jkt != *jkt {
        return Err(RefreshError::WrongKey);
    }

    let new_refresh_token = RefreshToken::generate();
    let rotated =
        refresh_tokens::rotate(&mut tx, &digest, &new_refresh_token.digest(), REFRESH_TTL).await?;
    let Some(refresh_expires_in) = rotated else {
        return reuse_detected(tx, presented.session_id).await;
    };
    tx.commit().await?;

    Ok(Issued {
        device_id: presented.device_id,
        access_token: token_issuer.issue(
            presented.user_id,
            presented.session_id,
            presented.auth_time,
            jkt,
        ),
        refresh_token: new_refresh_token,
        refresh_expires_in,
    })
}

async fn reuse_detected<T>(
    mut tx: PgTransaction<'_>,
    session_id: SessionId,
) -> Result<T, RefreshError> {
    sessions::revoke(&mut tx, session_id, RevokeReason::ReuseDetected).await?;
    tx.commit().await?;
    Err(RefreshError::Revoked(Some(RevokeReason::ReuseDetected)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::RevokeReason;
    use crate::testing::{
        JKT, assert_expires_after, count, expire, expires_in_secs, insert_device, issue_session,
        token_issuer,
    };
    use sqlx::PgPool;
    use uuid::Uuid;

    #[sqlx::test]
    async fn issue_binds_a_session_to_the_device_and_key(db: PgPool) {
        let (user_id, device_id) = insert_device(&db).await;
        let mut tx = db.begin().await.unwrap();

        let issued = issue(&mut tx, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        assert_eq!(issued.device_id, device_id);
        assert_eq!(issued.refresh_expires_in, REFRESH_TTL);
        let stored: (Uuid, Vec<u8>) = sqlx::query_as("select device_id, dpop_jkt from sessions")
            .fetch_one(&db)
            .await
            .unwrap();
        assert_eq!(stored, (device_id.as_uuid(), JKT.to_vec()));
        assert_expires_after(expires_in_secs(&db, "sessions").await, SESSION_LIFETIME);
    }

    #[sqlx::test]
    async fn issue_signs_an_access_token_for_the_stored_session(db: PgPool) {
        let (user_id, device_id) = insert_device(&db).await;
        let mut tx = db.begin().await.unwrap();

        let issued = issue(&mut tx, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        let claims = token_issuer().verify(issued.access_token.expose()).unwrap();
        let (session_id, auth_time): (Uuid, i64) =
            sqlx::query_as("select id, extract(epoch from auth_time)::int8 from sessions")
                .fetch_one(&db)
                .await
                .unwrap();
        assert_eq!(claims.user_id(), user_id);
        assert_eq!(claims.session_id().as_uuid(), session_id);
        assert_eq!(claims.jkt(), &JKT);
        assert_eq!(claims.auth_time(), auth_time as u64);
        assert!(claims.is_fresh());
    }

    #[sqlx::test]
    async fn issue_stores_only_the_refresh_token_hash(db: PgPool) {
        let (user_id, device_id) = insert_device(&db).await;
        let mut tx = db.begin().await.unwrap();

        let issued = issue(&mut tx, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();
        tx.commit().await.unwrap();

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
        let mut tx = db.begin().await.unwrap();

        let first = issue(&mut tx, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();
        sessions::revoke_by_device(&mut tx, device_id, RevokeReason::Replaced)
            .await
            .unwrap();
        let second = issue(&mut tx, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        assert_ne!(first.refresh_token.digest(), second.refresh_token.digest());
        assert_eq!(count(&db, "sessions").await, 2);
        assert_eq!(count(&db, "refresh_tokens").await, 2);
    }

    #[sqlx::test]
    async fn a_device_has_at_most_one_live_session(db: PgPool) {
        let (user_id, device_id) = insert_device(&db).await;
        let mut tx = db.begin().await.unwrap();
        issue(&mut tx, &token_issuer(), user_id, device_id, &JKT)
            .await
            .unwrap();
        tx.commit().await.unwrap();

        let mut tx = db.begin().await.unwrap();
        let err = issue(&mut tx, &token_issuer(), user_id, device_id, &JKT)
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
        let mut tx = db.begin().await.unwrap();

        let err = issue(&mut tx, &token_issuer(), user_id, DeviceId::random(), &JKT)
            .await
            .err()
            .unwrap();

        assert!(err.as_database_error().unwrap().is_foreign_key_violation());
        assert_eq!(count(&db, "sessions").await, 0);
        assert_eq!(count(&db, "refresh_tokens").await, 0);
    }

    async fn issued(db: &PgPool) -> Issued {
        let (user_id, device_id) = insert_device(db).await;
        issue_session(db, user_id, device_id, &JKT).await
    }

    async fn revoke_reason(db: &PgPool) -> Option<String> {
        sqlx::query_scalar("select revoke_reason from sessions")
            .fetch_one(db)
            .await
            .unwrap()
    }

    #[sqlx::test]
    async fn refresh_rotates_within_the_same_session(db: PgPool) {
        let first = issued(&db).await;
        sqlx::query("update sessions set auth_time = to_timestamp(1791504000)")
            .execute(&db)
            .await
            .unwrap();

        let refreshed = refresh(&db, &token_issuer(), &first.refresh_token, &JKT)
            .await
            .unwrap();

        let before = token_issuer().verify(first.access_token.expose()).unwrap();
        let after = token_issuer()
            .verify(refreshed.access_token.expose())
            .unwrap();
        assert_eq!(after.session_id(), before.session_id());
        assert_eq!(after.user_id(), before.user_id());
        assert_eq!(after.jkt(), &JKT);
        assert_eq!(after.auth_time(), 1_791_504_000);
        assert_eq!(refreshed.device_id, first.device_id);
        assert_ne!(
            refreshed.refresh_token.digest(),
            first.refresh_token.digest()
        );
        assert!(REFRESH_TTL - refreshed.refresh_expires_in <= Duration::from_secs(5));
        assert_eq!(count(&db, "refresh_tokens").await, 2);
    }

    #[sqlx::test]
    async fn refreshed_token_can_be_refreshed_again(db: PgPool) {
        let first = issued(&db).await;
        let second = refresh(&db, &token_issuer(), &first.refresh_token, &JKT)
            .await
            .unwrap();

        let third = refresh(&db, &token_issuer(), &second.refresh_token, &JKT).await;

        assert!(third.is_ok());
    }

    #[sqlx::test]
    async fn reusing_a_rotated_token_revokes_the_session(db: PgPool) {
        let first = issued(&db).await;
        let second = refresh(&db, &token_issuer(), &first.refresh_token, &JKT)
            .await
            .unwrap();

        let reused = refresh(&db, &token_issuer(), &first.refresh_token, &JKT).await;

        assert!(matches!(
            reused,
            Err(RefreshError::Revoked(Some(RevokeReason::ReuseDetected))),
        ));
        assert_eq!(revoke_reason(&db).await.as_deref(), Some("reuse_detected"));
        let child = refresh(&db, &token_issuer(), &second.refresh_token, &JKT).await;
        assert!(matches!(
            child,
            Err(RefreshError::Revoked(Some(RevokeReason::ReuseDetected))),
        ));
    }

    #[sqlx::test]
    async fn reuse_from_another_key_revokes_the_session_too(db: PgPool) {
        let first = issued(&db).await;
        refresh(&db, &token_issuer(), &first.refresh_token, &JKT)
            .await
            .unwrap();

        let reused = refresh(&db, &token_issuer(), &first.refresh_token, &[0x41; 32]).await;

        assert!(matches!(
            reused,
            Err(RefreshError::Revoked(Some(RevokeReason::ReuseDetected))),
        ));
        assert_eq!(revoke_reason(&db).await.as_deref(), Some("reuse_detected"));
    }

    #[sqlx::test]
    async fn a_live_token_from_another_key_stays_live(db: PgPool) {
        let first = issued(&db).await;

        let wrong = refresh(&db, &token_issuer(), &first.refresh_token, &[0x41; 32]).await;

        assert!(matches!(wrong, Err(RefreshError::WrongKey)));
        assert_eq!(revoke_reason(&db).await, None);
        assert!(
            refresh(&db, &token_issuer(), &first.refresh_token, &JKT)
                .await
                .is_ok()
        );
    }

    #[sqlx::test]
    async fn concurrent_refreshes_with_one_token_revoke_the_session(db: PgPool) {
        let first = issued(&db).await;
        let issuer = token_issuer();

        let (a, b) = tokio::join!(
            refresh(&db, &issuer, &first.refresh_token, &JKT),
            refresh(&db, &issuer, &first.refresh_token, &JKT),
        );

        let reused = |result: &Result<Issued, RefreshError>| {
            matches!(
                result,
                Err(RefreshError::Revoked(Some(RevokeReason::ReuseDetected)))
            )
        };
        assert!(a.is_ok() != b.is_ok(), "exactly one refresh succeeds");
        assert!(reused(&a) || reused(&b));
        assert_eq!(count(&db, "refresh_tokens").await, 2);
        assert_eq!(revoke_reason(&db).await.as_deref(), Some("reuse_detected"));
    }

    #[sqlx::test]
    async fn expired_token_is_refused_without_revoking(db: PgPool) {
        let first = issued(&db).await;
        expire(&db, "refresh_tokens").await;

        let result = refresh(&db, &token_issuer(), &first.refresh_token, &JKT).await;

        assert!(matches!(result, Err(RefreshError::Expired)));
        assert_eq!(revoke_reason(&db).await, None);
    }

    #[sqlx::test]
    async fn token_of_a_revoked_session_answers_its_reason(db: PgPool) {
        let first = issued(&db).await;
        let mut conn = db.acquire().await.unwrap();
        sessions::revoke_by_device(&mut conn, first.device_id, RevokeReason::Replaced)
            .await
            .unwrap();

        let result = refresh(&db, &token_issuer(), &first.refresh_token, &JKT).await;

        assert!(matches!(
            result,
            Err(RefreshError::Revoked(Some(RevokeReason::Replaced))),
        ));
    }

    #[sqlx::test]
    async fn unknown_token_is_revoked_without_a_reason(db: PgPool) {
        let result = refresh(&db, &token_issuer(), &RefreshToken::generate(), &JKT).await;

        assert!(matches!(result, Err(RefreshError::Revoked(None))));
    }
}
