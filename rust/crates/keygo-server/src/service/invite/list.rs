use super::Invite;
use crate::db::invites;
use sqlx::PgPool;

pub async fn list(db: &PgPool) -> sqlx::Result<Vec<Invite>> {
    let mut conn = db.acquire().await?;
    let open = invites::list(&mut conn).await?;
    Ok(Invite::all(&open))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{insert_invite as insert, invite_hash as hash};

    fn ids(invites: &[Invite]) -> Vec<String> {
        invites.iter().map(|invite| invite.id.to_string()).collect()
    }

    #[sqlx::test]
    async fn ids_grow_until_they_are_unique(db: PgPool) {
        insert(&db, hash(&[0xa3, 0xf9, 0xc1, 0x70])).await;
        insert(&db, hash(&[0xa3, 0xf9, 0xc1, 0xe0])).await;

        let mut ids = ids(&list(&db).await.unwrap());
        ids.sort();

        assert_eq!(ids, ["a3f9c17", "a3f9c1e"]);
    }

    #[sqlx::test]
    async fn keeps_the_oldest_first_order(db: PgPool) {
        insert(&db, hash(&[0x11])).await;
        insert(&db, hash(&[0x22])).await;
        sqlx::query(
            "update invites set created_at = created_at - interval '1 hour' where code_hash = $1",
        )
        .bind(hash(&[0x22]).as_bytes().as_slice())
        .execute(&db)
        .await
        .unwrap();

        assert_eq!(ids(&list(&db).await.unwrap()), ["22000", "11000"]);
    }
}
