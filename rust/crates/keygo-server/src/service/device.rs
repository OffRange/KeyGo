use crate::db::{devices, sessions};
use crate::domain::{Device, DeviceId, DeviceInfo, RevokeReason, UserId};
use sqlx::PgTransaction;

pub async fn attach_or_create(
    tx: &mut PgTransaction<'_>,
    user_id: UserId,
    requested: Device,
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::users;
    use crate::testing::{JKT, count, device, insert_device, issue_session, username};
    use serde_json::json;
    use sqlx::PgPool;
    use uuid::Uuid;

    async fn claim_once(db: &PgPool, user_id: UserId, requested: Device) -> DeviceId {
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

        let device_id = claim_once(&db, user_id, device(None)).await;

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
        let requested = Device {
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

        claim_once(&db, user_id, device(Some(existing))).await;

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

        claim_once(&db, user_id, device(Some(existing))).await;

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

        let device_id = claim_once(&db, user_id, device(Some(foreign))).await;

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

        let device_id = claim_once(&db, user_id, device(Some(unknown))).await;

        assert_ne!(device_id, unknown);
        assert_eq!(count(&db, "devices").await, 2);
    }
}
