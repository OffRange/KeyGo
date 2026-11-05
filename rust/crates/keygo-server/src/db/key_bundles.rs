use crate::domain::{KeyBundle, UserId};
use sqlx::PgConnection;

pub async fn insert(
    conn: &mut PgConnection,
    user_id: UserId,
    key_bundle: &KeyBundle,
) -> sqlx::Result<()> {
    sqlx::query!(
        "insert into key_bundles (user_id, wrapped_ark) values ($1, $2)",
        user_id.as_uuid(),
        key_bundle.wrapped_ark.to_bytes(),
    )
    .execute(conn)
    .await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::users;
    use crate::domain::SealedBlob;
    use crate::testing::{WRAPPED_ARK, count, key_bundle, username};
    use sqlx::PgPool;

    async fn insert_user(db: &PgPool, name: &str) -> UserId {
        let user_id = UserId::random();
        let mut conn = db.acquire().await.unwrap();
        users::insert(&mut conn, user_id, &username(name))
            .await
            .unwrap();
        user_id
    }

    #[sqlx::test]
    async fn blob_is_stored_byte_for_byte(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;
        let mut conn = db.acquire().await.unwrap();

        insert(&mut conn, user_id, &key_bundle()).await.unwrap();

        let stored: (uuid::Uuid, Vec<u8>) =
            sqlx::query_as("select user_id, wrapped_ark from key_bundles")
                .fetch_one(&db)
                .await
                .unwrap();
        assert_eq!(stored, (user_id.as_uuid(), WRAPPED_ARK.to_vec()));
    }

    #[sqlx::test]
    async fn stored_blob_decodes_to_the_same_parts(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, user_id, &key_bundle()).await.unwrap();

        let stored: Vec<u8> = sqlx::query_scalar("select wrapped_ark from key_bundles")
            .fetch_one(&db)
            .await
            .unwrap();

        let blob = SealedBlob::from_bytes(&stored).unwrap();
        assert_eq!(blob.nonce(), &WRAPPED_ARK[..12]);
        assert_eq!(blob.ciphertext(), &WRAPPED_ARK[12..]);
    }

    #[sqlx::test]
    async fn updated_at_comes_from_the_database(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;
        let mut conn = db.acquire().await.unwrap();

        insert(&mut conn, user_id, &key_bundle()).await.unwrap();

        let age: f64 = sqlx::query_scalar(
            "select extract(epoch from now() - updated_at)::float8 from key_bundles",
        )
        .fetch_one(&db)
        .await
        .unwrap();
        assert!((0.0..5.0).contains(&age), "{age}");
    }

    #[sqlx::test]
    async fn a_bundle_must_hold_a_wrapped_ark(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;

        let err = sqlx::query("insert into key_bundles (user_id) values ($1)")
            .bind(user_id.as_uuid())
            .execute(&db)
            .await
            .unwrap_err();

        assert_eq!(err.as_database_error().unwrap().code().unwrap(), "23502");
    }

    #[sqlx::test]
    async fn bundle_requires_a_user(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();

        let err = insert(&mut conn, UserId::random(), &key_bundle())
            .await
            .unwrap_err();

        assert!(err.as_database_error().unwrap().is_foreign_key_violation());
    }

    #[sqlx::test]
    async fn a_user_has_one_bundle(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, user_id, &key_bundle()).await.unwrap();

        let err = insert(&mut conn, user_id, &key_bundle()).await.unwrap_err();

        assert!(err.as_database_error().unwrap().is_unique_violation());
        assert_eq!(count(&db, "key_bundles").await, 1);
    }

    #[sqlx::test]
    async fn bundle_is_deleted_with_its_user(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, user_id, &key_bundle()).await.unwrap();

        sqlx::query("delete from users where id = $1")
            .bind(user_id.as_uuid())
            .execute(&db)
            .await
            .unwrap();

        assert_eq!(count(&db, "key_bundles").await, 0);
    }
}
