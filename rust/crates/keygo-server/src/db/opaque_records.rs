use super::corrupt;
use crate::domain::Username;
use keygo_opaque::{PasswordFile, SUITE_ID};
use sqlx::PgConnection;
use uuid::Uuid;
use zeroize::Zeroizing;

pub async fn insert(
    conn: &mut PgConnection,
    user_id: Uuid,
    file: &PasswordFile,
) -> sqlx::Result<()> {
    let record = file.to_bytes();
    sqlx::query!(
        "INSERT INTO opaque_records (user_id, suite, record, updated_at)
         VALUES ($1, $2, $3, now())",
        user_id,
        SUITE_ID,
        record.as_slice(),
    )
    .execute(conn)
    .await?;

    Ok(())
}

pub struct Record {
    pub user_id: Uuid,
    pub file: PasswordFile,
}

pub async fn find_by_username(
    conn: &mut PgConnection,
    username: &Username,
) -> sqlx::Result<Option<Record>> {
    let Some(row) = sqlx::query!(
        "SELECT r.user_id, r.suite, r.record
         FROM opaque_records r JOIN users u ON u.id = r.user_id
         WHERE u.username = $1",
        username.as_str(),
    )
    .fetch_optional(conn)
    .await?
    else {
        return Ok(None);
    };

    if row.suite != SUITE_ID {
        return Err(corrupt(format!(
            "opaque record uses unknown suite {:?}",
            row.suite
        )));
    }

    let record = Zeroizing::new(row.record);
    let file = PasswordFile::from_bytes(&record).map_err(corrupt)?;

    Ok(Some(Record {
        user_id: row.user_id,
        file,
    }))
}
