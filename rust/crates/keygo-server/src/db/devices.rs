use crate::db::corrupt;
use crate::domain::{Device, DeviceId, DeviceInfo, UserId};
use sqlx::PgConnection;
use uuid::Uuid;

pub async fn insert(
    conn: &mut PgConnection,
    id: DeviceId,
    user_id: UserId,
    device_info: &DeviceInfo,
) -> sqlx::Result<()> {
    sqlx::query!(
        "insert into devices (id, user_id, name, platform, app_version)
         values ($1, $2, $3, $4, $5)",
        id.as_uuid(),
        user_id.as_uuid(),
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
    user_id: UserId,
    device_info: &DeviceInfo,
) -> sqlx::Result<bool> {
    let result = sqlx::query!(
        "update devices
         set name = $3, platform = $4, app_version = $5, last_seen_at = now()
         where id = $1 and user_id = $2",
        id.as_uuid(),
        user_id.as_uuid(),
        device_info.name.as_str(),
        device_info.platform.as_str(),
        device_info.app_version.as_str(),
    )
    .execute(conn)
    .await?;

    Ok(result.rows_affected() == 1)
}

struct DeviceRow {
    id: Uuid,
    name: String,
    platform: String,
    app_version: String,
    created_at: i64,
    last_seen_at: i64,
    auth_time: Option<i64>,
    logged_in: bool,
}

impl TryFrom<DeviceRow> for Device {
    type Error = sqlx::Error;

    fn try_from(row: DeviceRow) -> sqlx::Result<Self> {
        Ok(Self {
            id: row.id.into(),
            info: DeviceInfo {
                name: row.name.try_into().map_err(corrupt)?,
                platform: row.platform.parse().map_err(corrupt)?,
                app_version: row.app_version.try_into().map_err(corrupt)?,
            },
            created_at: u64::try_from(row.created_at).map_err(corrupt)?,
            last_seen_at: u64::try_from(row.last_seen_at).map_err(corrupt)?,
            auth_time: row
                .auth_time
                .map(u64::try_from)
                .transpose()
                .map_err(corrupt)?,
            logged_in: row.logged_in,
        })
    }
}

pub async fn list(conn: &mut PgConnection, user_id: UserId) -> sqlx::Result<Vec<Device>> {
    sqlx::query_as!(
        DeviceRow,
        r#"select d.id,
           d.name,
           d.platform,
           d.app_version,
           extract(epoch from d.created_at)::int8   as "created_at!",
           extract(epoch from d.last_seen_at)::int8 as "last_seen_at!",
           extract(epoch from s.auth_time)::int8    as "auth_time?",
           s.id is not null                         as "logged_in!"
           from devices d
           left join sessions s on s.device_id = d.id and s.revoked_at is null and s.expires_at > now()
           where d.user_id = $1
           order by d.created_at"#,
        user_id.as_uuid(),
    )
        .fetch_all(conn)
        .await?
        .into_iter()
        .map(Device::try_from)
        .collect()
}

pub async fn delete(conn: &mut PgConnection, id: DeviceId, user_id: UserId) -> sqlx::Result<()> {
    sqlx::query!(
        "delete from devices where id = $1 and user_id = $2",
        id.as_uuid(),
        user_id.as_uuid(),
    )
    .execute(conn)
    .await?;

    Ok(())
}
