use crate::db::users;
use keygo_core::UserId;
use sqlx::PgPool;

pub async fn delete(db: &PgPool, user_id: UserId) -> sqlx::Result<()> {
    let mut conn = db.acquire().await?;
    users::delete(&mut conn, user_id).await
}
