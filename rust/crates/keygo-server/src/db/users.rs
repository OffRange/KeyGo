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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::username;
    use sqlx::PgPool;

    #[sqlx::test]
    async fn inserted_user_is_found_by_name_and_id(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let user_id = Uuid::new_v4();

        insert(&mut conn, user_id, &username("ada")).await.unwrap();

        assert!(username_exists(&mut conn, &username("ada")).await.unwrap());
        assert!(id_exists(&mut conn, user_id).await.unwrap());
        assert_eq!(
            find_username(&mut conn, user_id).await.unwrap(),
            Some(username("ada")),
        );
    }

    #[sqlx::test]
    async fn unknown_user_is_absent(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();

        assert!(!username_exists(&mut conn, &username("ada")).await.unwrap());
        assert!(!id_exists(&mut conn, Uuid::new_v4()).await.unwrap());
        assert_eq!(
            find_username(&mut conn, Uuid::new_v4()).await.unwrap(),
            None
        );
    }

    #[sqlx::test]
    async fn usernames_and_ids_are_unique(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let user_id = Uuid::new_v4();
        insert(&mut conn, user_id, &username("ada")).await.unwrap();

        let same_name = insert(&mut conn, Uuid::new_v4(), &username("ada")).await;
        let same_id = insert(&mut conn, user_id, &username("bob")).await;

        for result in [same_name, same_id] {
            let err = result.unwrap_err();
            assert!(err.as_database_error().unwrap().is_unique_violation());
        }
    }

    #[sqlx::test]
    async fn stored_invalid_username_is_corrupt(db: PgPool) {
        let user_id = Uuid::new_v4();
        sqlx::query("INSERT INTO users (id, username) VALUES ($1, 'NOT VALID')")
            .bind(user_id)
            .execute(&db)
            .await
            .unwrap();
        let mut conn = db.acquire().await.unwrap();

        let err = find_username(&mut conn, user_id).await.unwrap_err();

        assert!(matches!(err, sqlx::Error::Decode(_)));
    }
}
