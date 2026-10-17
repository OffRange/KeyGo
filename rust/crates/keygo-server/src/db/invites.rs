use super::corrupt;
use crate::domain::InviteHash;
use sqlx::PgConnection;
use std::time::Duration;
use time::OffsetDateTime;

#[derive(Debug)]
pub struct OpenInvite {
    pub code_hash: InviteHash,
    pub created_at: OffsetDateTime,
    pub expires_at: OffsetDateTime,
}

pub async fn insert(
    conn: &mut PgConnection,
    code_hash: &InviteHash,
    ttl: Duration,
) -> sqlx::Result<OpenInvite> {
    let row = sqlx::query!(
        "insert into invites (code_hash, expires_at)
         values ($1, now() + make_interval(secs => $2))
         returning created_at, expires_at",
        code_hash.as_bytes().as_slice(),
        ttl.as_secs_f64(),
    )
    .fetch_one(conn)
    .await?;

    Ok(OpenInvite {
        code_hash: *code_hash,
        created_at: row.created_at,
        expires_at: row.expires_at,
    })
}

pub async fn delete_expired(conn: &mut PgConnection) -> sqlx::Result<u64> {
    let result = sqlx::query!("delete from invites where expires_at <= now()")
        .execute(conn)
        .await?;

    Ok(result.rows_affected())
}

pub async fn list(conn: &mut PgConnection) -> sqlx::Result<Vec<OpenInvite>> {
    let rows = sqlx::query!(
        "select code_hash, created_at, expires_at from invites
         where expires_at > now()
         order by created_at, code_hash",
    )
    .fetch_all(conn)
    .await?;

    rows.into_iter()
        .map(|row| {
            Ok(OpenInvite {
                code_hash: row.code_hash.as_slice().try_into().map_err(corrupt)?,
                created_at: row.created_at,
                expires_at: row.expires_at,
            })
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{count, expire, expires_in_secs};
    use sqlx::PgPool;

    const TTL: Duration = Duration::from_hours(24);

    fn hash(byte: u8) -> InviteHash {
        [byte; 32].into()
    }

    #[sqlx::test]
    async fn invite_stores_its_hash(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();

        insert(&mut conn, &hash(7), TTL).await.unwrap();

        let stored: Vec<u8> = sqlx::query_scalar("select code_hash from invites")
            .fetch_one(&db)
            .await
            .unwrap();
        assert_eq!(stored, [7; 32]);
    }

    #[sqlx::test]
    async fn invite_expires_after_ttl(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();

        let invite = insert(&mut conn, &hash(7), TTL).await.unwrap();

        assert_eq!(invite.expires_at - invite.created_at, TTL);
        let remaining = expires_in_secs(&db, "invites").await;
        assert!(
            (TTL.as_secs_f64() - 5.0..=TTL.as_secs_f64()).contains(&remaining),
            "{remaining}"
        );
    }

    #[sqlx::test]
    async fn duplicate_hash_is_rejected(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, &hash(7), TTL).await.unwrap();

        let err = insert(&mut conn, &hash(7), TTL).await.unwrap_err();

        assert!(err.as_database_error().unwrap().is_unique_violation());
        assert_eq!(count(&db, "invites").await, 1);
    }

    #[sqlx::test]
    async fn delete_expired_keeps_open_invites(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, &hash(1), TTL).await.unwrap();
        expire(&db, "invites").await;
        insert(&mut conn, &hash(2), TTL).await.unwrap();

        assert_eq!(delete_expired(&mut conn).await.unwrap(), 1);
        assert_eq!(count(&db, "invites").await, 1);
        let open: Vec<_> = list(&mut conn)
            .await
            .unwrap()
            .into_iter()
            .map(|invite| invite.code_hash)
            .collect();
        assert_eq!(open, [hash(2)]);
    }

    #[sqlx::test]
    async fn list_keeps_expired_rows_in_the_table(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, &hash(7), TTL).await.unwrap();
        expire(&db, "invites").await;

        list(&mut conn).await.unwrap();

        assert_eq!(count(&db, "invites").await, 1);
    }

    #[sqlx::test]
    async fn list_returns_open_invites_oldest_first(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, &hash(1), TTL).await.unwrap();
        insert(&mut conn, &hash(2), TTL).await.unwrap();
        sqlx::query(
            "update invites set created_at = created_at - interval '1 hour' where code_hash = $1",
        )
        .bind([2u8; 32].as_slice())
        .execute(&db)
        .await
        .unwrap();

        let hashes: Vec<_> = list(&mut conn)
            .await
            .unwrap()
            .into_iter()
            .map(|invite| invite.code_hash)
            .collect();

        assert_eq!(hashes, [hash(2), hash(1)]);
    }

    #[sqlx::test]
    async fn list_skips_expired_invites(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        insert(&mut conn, &hash(7), TTL).await.unwrap();
        expire(&db, "invites").await;

        assert!(list(&mut conn).await.unwrap().is_empty());
    }
}
