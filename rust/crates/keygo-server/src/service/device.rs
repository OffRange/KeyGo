use crate::db::{devices, sessions};
use crate::domain::{Claims, Device, DeviceClaim, DeviceId, DeviceInfo, RevokeReason, UserId};
use sqlx::{PgPool, PgTransaction};
use thiserror::Error;

pub async fn attach_or_create(
    tx: &mut PgTransaction<'_>,
    user_id: UserId,
    requested: DeviceClaim,
) -> sqlx::Result<DeviceId> {
    if let Some(id) = requested.id
        && devices::attach(tx, id, user_id, &requested.info).await?
    {
        sessions::revoke_by_device(tx, id, RevokeReason::Replaced).await?;
        return Ok(id);
    }

    create(tx, user_id, &requested.info).await
}

pub async fn create(
    tx: &mut PgTransaction<'_>,
    user_id: UserId,
    info: &DeviceInfo,
) -> sqlx::Result<DeviceId> {
    let device_id = DeviceId::random();
    devices::insert(tx, device_id, user_id, info).await?;

    Ok(device_id)
}

pub async fn get_devices(db: &PgPool, user_id: UserId) -> sqlx::Result<Vec<Device>> {
    let mut conn = db.acquire().await?;
    devices::list(&mut conn, user_id).await
}

#[derive(Debug, Error)]
pub enum DeleteDeviceError {
    #[error("recent sign-in required")]
    FreshAuthRequired,
    #[error("database error")]
    Db(#[from] sqlx::Error),
}

pub async fn delete_device(
    db: &PgPool,
    device_id: DeviceId,
    claims: &Claims,
) -> Result<(), DeleteDeviceError> {
    let mut conn = db.acquire().await?;
    let own = sessions::device_id(&mut conn, claims.session_id()).await?;
    if own != Some(device_id) && !claims.is_fresh() {
        return Err(DeleteDeviceError::FreshAuthRequired);
    }

    devices::delete(&mut conn, device_id, claims.user_id()).await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::users;
    use crate::domain::{FRESH_AUTH_WINDOW, unix_now};
    use crate::service::session::Issued;
    use crate::testing::{
        JKT, count, device_claim, device_info, insert_device, issue_session, token_issuer, username,
    };
    use serde_json::json;
    use sqlx::PgPool;
    use uuid::Uuid;

    async fn claim_once(db: &PgPool, user_id: UserId, requested: DeviceClaim) -> DeviceId {
        let mut tx = db.begin().await.unwrap();
        let device_id = attach_or_create(&mut tx, user_id, requested).await.unwrap();
        tx.commit().await.unwrap();
        device_id
    }

    async fn revoke_reasons(db: &PgPool, device_id: DeviceId) -> Vec<Option<String>> {
        sqlx::query_scalar("select revoke_reason from sessions where device_id = $1")
            .bind(device_id.as_uuid())
            .fetch_all(db)
            .await
            .unwrap()
    }

    #[sqlx::test]
    async fn no_id_creates_a_device(db: PgPool) {
        let (user_id, existing) = insert_device(&db).await;

        let device_id = claim_once(&db, user_id, device_claim(None)).await;

        assert_ne!(device_id, existing);
        assert_eq!(count(&db, "devices").await, 2);
    }

    #[sqlx::test]
    async fn own_device_is_attached_and_updated(db: PgPool) {
        let (user_id, existing) = insert_device(&db).await;
        let info: DeviceInfo = serde_json::from_value(
            json!({ "name": "Pixel 10", "platform": "android", "app_version": "2.4.0" }),
        )
        .unwrap();
        let requested = DeviceClaim {
            id: Some(existing),
            info,
        };

        let device_id = claim_once(&db, user_id, requested).await;

        assert_eq!(device_id, existing);
        assert_eq!(count(&db, "devices").await, 1);
        let stored: (String, String) = sqlx::query_as("select name, app_version from devices")
            .fetch_one(&db)
            .await
            .unwrap();
        assert_eq!(stored, ("Pixel 10".into(), "2.4.0".into()));
    }

    #[sqlx::test]
    async fn attaching_revokes_the_live_session_as_replaced(db: PgPool) {
        let (user_id, existing) = insert_device(&db).await;
        issue_session(&db, user_id, existing, &JKT).await;

        claim_once(&db, user_id, device_claim(Some(existing))).await;

        assert_eq!(
            revoke_reasons(&db, existing).await,
            [Some("replaced".to_owned())]
        );
    }

    #[sqlx::test]
    async fn revoked_sessions_keep_their_reason(db: PgPool) {
        let (user_id, existing) = insert_device(&db).await;
        issue_session(&db, user_id, existing, &JKT).await;
        sqlx::query("update sessions set revoked_at = now(), revoke_reason = 'logout'")
            .execute(&db)
            .await
            .unwrap();

        claim_once(&db, user_id, device_claim(Some(existing))).await;

        assert_eq!(
            revoke_reasons(&db, existing).await,
            [Some("logout".to_owned())]
        );
    }

    #[sqlx::test]
    async fn another_users_device_is_not_attached(db: PgPool) {
        let (owner, foreign) = insert_device(&db).await;
        issue_session(&db, owner, foreign, &JKT).await;
        let user_id = UserId::random();
        let mut conn = db.acquire().await.unwrap();
        users::insert(&mut conn, user_id, &username("bob"))
            .await
            .unwrap();

        let device_id = claim_once(&db, user_id, device_claim(Some(foreign))).await;

        assert_ne!(device_id, foreign);
        assert_eq!(revoke_reasons(&db, foreign).await, [None]);
        let owners: Vec<Uuid> =
            sqlx::query_scalar("select user_id from devices order by user_id = $1")
                .bind(owner.as_uuid())
                .fetch_all(&db)
                .await
                .unwrap();
        assert_eq!(owners, [user_id.as_uuid(), owner.as_uuid()]);
    }

    #[sqlx::test]
    async fn unknown_device_id_creates_a_device(db: PgPool) {
        let (user_id, _) = insert_device(&db).await;
        let unknown = DeviceId::random();

        let device_id = claim_once(&db, user_id, device_claim(Some(unknown))).await;

        assert_ne!(device_id, unknown);
        assert_eq!(count(&db, "devices").await, 2);
    }

    #[sqlx::test]
    async fn listed_device_reflects_only_its_live_session(db: PgPool) {
        let (user_id, existing) = insert_device(&db).await;
        issue_session(&db, user_id, existing, &JKT).await;
        claim_once(&db, user_id, device_claim(Some(existing))).await;
        issue_session(&db, user_id, existing, &JKT).await;

        let listed = get_devices(&db, user_id).await.unwrap();

        assert_eq!(listed.len(), 1);
        assert!(listed[0].logged_in);
        assert!(listed[0].auth_time.is_some());
    }

    #[sqlx::test]
    async fn logged_out_device_is_still_listed(db: PgPool) {
        let (user_id, existing) = insert_device(&db).await;
        issue_session(&db, user_id, existing, &JKT).await;
        sqlx::query("update sessions set revoked_at = now(), revoke_reason = 'logout'")
            .execute(&db)
            .await
            .unwrap();

        let listed = get_devices(&db, user_id).await.unwrap();

        assert_eq!(listed.len(), 1);
        assert!(!listed[0].logged_in);
        assert_eq!(listed[0].auth_time, None);
    }

    fn fresh_claims(issued: &Issued) -> Claims {
        token_issuer().verify(issued.access_token.expose()).unwrap()
    }

    fn stale_claims(issued: &Issued) -> Claims {
        let issuer = token_issuer();
        let claims = fresh_claims(issued);
        let auth_time = unix_now() - FRESH_AUTH_WINDOW.as_secs() - 1;
        let token = issuer.issue(claims.user_id(), claims.session_id(), auth_time, &JKT);
        issuer.verify(token.expose()).unwrap()
    }

    /// A signed-in device and a second device of the same user.
    async fn two_devices(db: &PgPool) -> (Issued, DeviceId) {
        let (user_id, own) = insert_device(db).await;
        let issued = issue_session(db, user_id, own, &JKT).await;
        let other = DeviceId::random();
        let mut conn = db.acquire().await.unwrap();
        devices::insert(&mut conn, other, user_id, &device_info())
            .await
            .unwrap();
        (issued, other)
    }

    async fn device_exists(db: &PgPool, device_id: DeviceId) -> bool {
        sqlx::query_scalar("select exists (select 1 from devices where id = $1)")
            .bind(device_id.as_uuid())
            .fetch_one(db)
            .await
            .unwrap()
    }

    #[sqlx::test]
    async fn stale_session_removes_its_own_device(db: PgPool) {
        let (issued, _) = two_devices(&db).await;

        delete_device(&db, issued.device_id, &stale_claims(&issued))
            .await
            .unwrap();

        assert!(!device_exists(&db, issued.device_id).await);
        assert_eq!(count(&db, "sessions").await, 0);
    }

    #[sqlx::test]
    async fn stale_session_cannot_remove_another_device(db: PgPool) {
        let (issued, other) = two_devices(&db).await;

        let result = delete_device(&db, other, &stale_claims(&issued)).await;

        assert!(matches!(result, Err(DeleteDeviceError::FreshAuthRequired)));
        assert!(device_exists(&db, other).await);
    }

    #[sqlx::test]
    async fn fresh_session_removes_another_device(db: PgPool) {
        let (issued, other) = two_devices(&db).await;

        delete_device(&db, other, &fresh_claims(&issued))
            .await
            .unwrap();

        assert!(!device_exists(&db, other).await);
        assert!(device_exists(&db, issued.device_id).await);
    }

    #[sqlx::test]
    async fn another_users_device_is_not_removed(db: PgPool) {
        let (_, foreign) = insert_device(&db).await;
        let user_id = UserId::random();
        let mut conn = db.acquire().await.unwrap();
        users::insert(&mut conn, user_id, &username("bob"))
            .await
            .unwrap();
        let own = DeviceId::random();
        devices::insert(&mut conn, own, user_id, &device_info())
            .await
            .unwrap();
        let issued = issue_session(&db, user_id, own, &JKT).await;

        delete_device(&db, foreign, &fresh_claims(&issued))
            .await
            .unwrap();

        assert!(device_exists(&db, foreign).await);
    }
}
