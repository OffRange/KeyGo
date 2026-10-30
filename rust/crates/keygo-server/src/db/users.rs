use super::corrupt;
use crate::domain::{UserId, Username};
use sqlx::PgConnection;

pub async fn username_exists(conn: &mut PgConnection, username: &Username) -> sqlx::Result<bool> {
    sqlx::query_scalar!(
        r#"select exists(select 1 from users where username = $1) as "exists!""#,
        username.as_str(),
    )
    .fetch_one(conn)
    .await
}

pub async fn id_exists(conn: &mut PgConnection, user_id: UserId) -> sqlx::Result<bool> {
    sqlx::query_scalar!(
        r#"select exists(select 1 from users where id = $1) as "exists!""#,
        user_id.as_uuid(),
    )
    .fetch_one(conn)
    .await
}

pub async fn insert(
    conn: &mut PgConnection,
    user_id: UserId,
    username: &Username,
) -> sqlx::Result<()> {
    sqlx::query!(
        "insert into users (id, username) values ($1, $2)",
        user_id.as_uuid(),
        username.as_str(),
    )
    .execute(conn)
    .await?;

    Ok(())
}

pub async fn find_username(
    conn: &mut PgConnection,
    user_id: UserId,
) -> sqlx::Result<Option<Username>> {
    let Some(username) = sqlx::query_scalar!(
        "select username from users where id = $1",
        user_id.as_uuid()
    )
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
        let user_id = UserId::random();

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
        assert!(!id_exists(&mut conn, UserId::random()).await.unwrap());
        assert_eq!(
            find_username(&mut conn, UserId::random()).await.unwrap(),
            None
        );
    }

    #[sqlx::test]
    async fn usernames_and_ids_are_unique(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let user_id = UserId::random();
        insert(&mut conn, user_id, &username("ada")).await.unwrap();

        let same_name = insert(&mut conn, UserId::random(), &username("ada")).await;
        let same_id = insert(&mut conn, user_id, &username("bob")).await;

        for result in [same_name, same_id] {
            let err = result.unwrap_err();
            assert!(err.as_database_error().unwrap().is_unique_violation());
        }
    }

    #[sqlx::test]
    async fn stored_invalid_username_is_corrupt(db: PgPool) {
        let user_id = UserId::random();
        sqlx::query("insert into users (id, username) values ($1, 'NOT VALID')")
            .bind(user_id.as_uuid())
            .execute(&db)
            .await
            .unwrap();
        let mut conn = db.acquire().await.unwrap();

        let err = find_username(&mut conn, user_id).await.unwrap_err();

        assert!(matches!(err, sqlx::Error::Decode(_)));
    }
}
