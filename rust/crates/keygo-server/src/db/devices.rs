use crate::domain::{DeviceId, DeviceInfo};
use sqlx::PgConnection;
use uuid::Uuid;

pub async fn insert(
    conn: &mut PgConnection,
    id: DeviceId,
    user_id: Uuid,
    device_info: &DeviceInfo,
) -> sqlx::Result<()> {
    sqlx::query!(
        "insert into devices (id, user_id, name, platform, app_version)
         values ($1, $2, $3, $4, $5)",
        id.as_uuid(),
        user_id,
        device_info.name.as_str(),
        device_info.platform.as_str(),
        device_info.app_version.as_str(),
    )
    .execute(conn)
    .await?;

    Ok(())
}

pub async fn attach(
    conn: &mut PgConnection,
    id: DeviceId,
    user_id: Uuid,
    device_info: &DeviceInfo,
) -> sqlx::Result<bool> {
    let result = sqlx::query!(
        "update devices
         set name = $3, platform = $4, app_version = $5, last_seen_at = now()
         where id = $1 and user_id = $2",
        id.as_uuid(),
        user_id,
        device_info.name.as_str(),
        device_info.platform.as_str(),
        device_info.app_version.as_str(),
    )
    .execute(conn)
    .await?;

    Ok(result.rows_affected() == 1)
}
