use crate::db::users;
use keygo_core::UserId;
use sqlx::PgPool;

pub async fn delete(db: &PgPool, user_id: UserId) -> sqlx::Result<()> {
    let mut conn = db.acquire().await?;
    users::delete(&mut conn, user_id).await
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{count, register};
    use keygo_opaque::server::ServerSetup;

    /// Every table that holds rows for a user, directly or through its devices.
    const OWNED: [&str; 6] = [
        "users",
        "opaque_records",
        "key_bundles",
        "devices",
        "sessions",
        "refresh_tokens",
    ];

    #[sqlx::test]
    async fn delete_removes_everything_the_account_owns(db: PgPool) {
        let user_id = register(&db, &ServerSetup::generate(), "ada").await;
        for table in OWNED {
            assert_eq!(count(&db, table).await, 1, "{table}");
        }

        delete(&db, user_id).await.unwrap();

        for table in OWNED {
            assert_eq!(count(&db, table).await, 0, "{table}");
        }
    }

    #[sqlx::test]
    async fn delete_keeps_other_accounts(db: PgPool) {
        let setup = ServerSetup::generate();
        let ada = register(&db, &setup, "ada").await;
        register(&db, &setup, "bob").await;

        delete(&db, ada).await.unwrap();

        for table in OWNED {
            assert_eq!(count(&db, table).await, 1, "{table}");
        }
    }

    #[sqlx::test]
    async fn delete_frees_the_username(db: PgPool) {
        let setup = ServerSetup::generate();
        let ada = register(&db, &setup, "ada").await;

        delete(&db, ada).await.unwrap();

        // `register` unwraps every step, so a taken name would panic here.
        register(&db, &setup, "ada").await;
    }
}
