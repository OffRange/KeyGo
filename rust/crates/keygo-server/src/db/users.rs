use super::corrupt;
use crate::domain::Username;
use sqlx::PgConnection;
use uuid::Uuid;

pub async fn username_exists(conn: &mut PgConnection, username: &Username) -> sqlx::Result<bool> {
    sqlx::query_scalar!(
        r#"SELECT EXISTS(SELECT 1 FROM users WHERE username = $1) AS "exists!""#,
        username.as_str(),
    )
    .fetch_one(conn)
    .await
}

pub async fn id_exists(conn: &mut PgConnection, user_id: Uuid) -> sqlx::Result<bool> {
    sqlx::query_scalar!(
        r#"SELECT EXISTS(SELECT 1 FROM users WHERE id = $1) AS "exists!""#,
        user_id,
    )
    .fetch_one(conn)
    .await
}

pub async fn insert(
    conn: &mut PgConnection,
    user_id: Uuid,
    username: &Username,
) -> sqlx::Result<()> {
    sqlx::query!(
        "INSERT INTO users (id, username) VALUES ($1, $2)",
        user_id,
        username.as_str(),
    )
    .execute(conn)
    .await?;

    Ok(())
}

pub async fn find_username(
    conn: &mut PgConnection,
    user_id: Uuid,
) -> sqlx::Result<Option<Username>> {
    let Some(username) = sqlx::query_scalar!("SELECT username FROM users WHERE id = $1", user_id)
        .fetch_optional(conn)
        .await?
    else {
        return Ok(None);
    };

    Username::parse(&username).map(Some).map_err(corrupt)
}
