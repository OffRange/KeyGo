use super::corrupt;
use crate::domain::{UserId, Username};
use keygo_opaque::{PasswordFile, SUITE_ID};
use sqlx::PgConnection;
use zeroize::Zeroizing;

pub async fn insert(
    conn: &mut PgConnection,
    user_id: UserId,
    file: &PasswordFile,
) -> sqlx::Result<()> {
    let record = file.to_bytes();
    sqlx::query!(
        "insert into opaque_records (user_id, suite, record, updated_at)
         values ($1, $2, $3, now())",
        user_id.as_uuid(),
        SUITE_ID,
        record.as_slice(),
    )
    .execute(conn)
    .await?;

    Ok(())
}

pub struct Record {
    pub user_id: UserId,
    pub file: PasswordFile,
}

pub async fn find_by_username(
    conn: &mut PgConnection,
    username: &Username,
) -> sqlx::Result<Option<Record>> {
    let Some(row) = sqlx::query!(
        "select r.user_id, r.suite, r.record
         from opaque_records r join users u on u.id = r.user_id
         where u.username = $1",
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
        user_id: row.user_id.into(),
        file,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::users;
    use crate::testing::{PASSWORD, username};
    use keygo_opaque::client::ClientRegistration;
    use keygo_opaque::server::{self, ServerSetup};
    use sqlx::PgPool;

    fn password_file(user_id: UserId) -> PasswordFile {
        let setup = ServerSetup::generate();
        let (client, request) = ClientRegistration::start(PASSWORD).unwrap();
        let response =
            server::registration_start(&setup, request, user_id.as_uuid().as_bytes()).unwrap();
        let finish = client.finish(PASSWORD, response).unwrap();
        server::registration_finish(finish.upload)
    }

    async fn insert_user(db: &PgPool, name: &str) -> UserId {
        let user_id = UserId::random();
        let mut conn = db.acquire().await.unwrap();
        users::insert(&mut conn, user_id, &username(name))
            .await
            .unwrap();
        user_id
    }

    async fn insert_raw(db: &PgPool, user_id: UserId, suite: &str, record: &[u8]) {
        sqlx::query(
            "insert into opaque_records (user_id, suite, record, updated_at)
             values ($1, $2, $3, now())",
        )
        .bind(user_id.as_uuid())
        .bind(suite)
        .bind(record)
        .execute(db)
        .await
        .unwrap();
    }

    #[sqlx::test]
    async fn record_is_found_by_username(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;
        let file = password_file(user_id);
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, user_id, &file).await.unwrap();

        let record = find_by_username(&mut conn, &username("ada"))
            .await
            .unwrap()
            .unwrap();

        assert_eq!(record.user_id, user_id);
        assert_eq!(*record.file.to_bytes(), *file.to_bytes());
    }

    #[sqlx::test]
    async fn record_stores_the_suite(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, user_id, &password_file(user_id))
            .await
            .unwrap();

        let suite: String = sqlx::query_scalar("select suite from opaque_records")
            .fetch_one(&db)
            .await
            .unwrap();

        assert_eq!(suite, SUITE_ID);
    }

    #[sqlx::test]
    async fn unknown_username_has_no_record(db: PgPool) {
        insert_user(&db, "ada").await;
        let mut conn = db.acquire().await.unwrap();

        for name in ["bob", "ada"] {
            let record = find_by_username(&mut conn, &username(name)).await.unwrap();
            assert!(record.is_none(), "{name}");
        }
    }

    #[sqlx::test]
    async fn record_requires_a_user(db: PgPool) {
        let user_id = UserId::random();
        let mut conn = db.acquire().await.unwrap();

        let err = insert(&mut conn, user_id, &password_file(user_id))
            .await
            .unwrap_err();

        assert!(err.as_database_error().unwrap().is_foreign_key_violation());
    }

    #[sqlx::test]
    async fn record_is_deleted_with_its_user(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, user_id, &password_file(user_id))
            .await
            .unwrap();

        sqlx::query("delete from users where id = $1")
            .bind(user_id.as_uuid())
            .execute(&db)
            .await
            .unwrap();

        assert_eq!(crate::testing::count(&db, "opaque_records").await, 0);
    }

    #[sqlx::test]
    async fn unknown_suite_is_corrupt(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;
        insert_raw(
            &db,
            user_id,
            "keygo-opaque-v0",
            &password_file(user_id).to_bytes(),
        )
        .await;
        let mut conn = db.acquire().await.unwrap();

        let err = find_by_username(&mut conn, &username("ada"))
            .await
            .err()
            .unwrap();

        assert!(matches!(err, sqlx::Error::Decode(_)));
    }

    #[sqlx::test]
    async fn undecodable_record_is_corrupt(db: PgPool) {
        let user_id = insert_user(&db, "ada").await;
        insert_raw(&db, user_id, SUITE_ID, &[0]).await;
        let mut conn = db.acquire().await.unwrap();

        let err = find_by_username(&mut conn, &username("ada"))
            .await
            .err()
            .unwrap();

        assert!(matches!(err, sqlx::Error::Decode(_)));
    }
}
