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
