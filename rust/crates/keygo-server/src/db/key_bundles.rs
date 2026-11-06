use super::corrupt;
use crate::domain::{KeyBundle, SealedBlob, StoredKeyBundle, UserId, check_wrapped_ark};
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

pub async fn find(
    conn: &mut PgConnection,
    user_id: UserId,
) -> sqlx::Result<Option<StoredKeyBundle>> {
    let Some(row) = sqlx::query!(
        r#"select wrapped_ark, extract(epoch from updated_at)::int8 as "updated_at!"
           from key_bundles
           where user_id = $1"#,
        user_id.as_uuid(),
    )
    .fetch_optional(conn)
    .await?
    else {
        return Ok(None);
    };

    // Registration admits only a well-sized wrap, so any other length is a corrupted row.
    let wrapped_ark = SealedBlob::from_bytes(&row.wrapped_ark).map_err(corrupt)?;
    check_wrapped_ark(&wrapped_ark).map_err(corrupt)?;

    Ok(Some(StoredKeyBundle {
        bundle: KeyBundle { wrapped_ark },
        updated_at: u64::try_from(row.updated_at).map_err(corrupt)?,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::users;
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

    #[sqlx::test]
    async fn find_returns_the_stored_bundle(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, user_id, &key_bundle()).await.unwrap();
        sqlx::query("update key_bundles set updated_at = to_timestamp(1700000000)")
            .execute(&db)
            .await
            .unwrap();

        let found = find(&mut conn, user_id).await.unwrap().unwrap();

        assert_eq!(found.bundle.wrapped_ark.to_bytes(), WRAPPED_ARK);
        assert_eq!(found.updated_at, 1_700_000_000);
    }

    #[sqlx::test]
    async fn find_ignores_other_users_bundles(db: PgPool) {
        let ada = insert_user(&db, "ada").await;
        let bob = insert_user(&db, "bob").await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, ada, &key_bundle()).await.unwrap();

        assert!(find(&mut conn, bob).await.unwrap().is_none());
    }

    #[sqlx::test]
    async fn missized_bundle_is_corrupt(db: PgPool) {
        // 11 B is too short for a nonce, 59 B and 61 B are a sealed blob but not a wrapped ARK.
        for len in [11, 59, 61] {
            let user_id = insert_user(&db, &format!("ada{len}")).await;
            sqlx::query("insert into key_bundles (user_id, wrapped_ark) values ($1, $2)")
                .bind(user_id.as_uuid())
                .bind(vec![7u8; len])
                .execute(&db)
                .await
                .unwrap();
            let mut conn = db.acquire().await.unwrap();

            let err = find(&mut conn, user_id).await.err().unwrap();

            assert!(matches!(err, sqlx::Error::Decode(_)), "{len} B");
        }
    }
}
