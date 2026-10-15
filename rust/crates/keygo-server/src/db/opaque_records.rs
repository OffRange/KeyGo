use keygo_opaque::{PasswordFile, SUITE_ID};
use sqlx::PgConnection;
use uuid::Uuid;

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
