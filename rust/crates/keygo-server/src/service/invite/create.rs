use super::Invite;
use crate::db::invites;
use crate::domain::{InviteCode, InvitePepper};
use sqlx::PgPool;
use std::time::Duration;

pub struct Created {
    pub code: InviteCode,
    pub invite: Invite,
}

pub async fn create(db: &PgPool, pepper: &InvitePepper, ttl: Duration) -> sqlx::Result<Created> {
    let code = InviteCode::generate();
    let mut conn = db.acquire().await?;
    let stored = invites::insert(&mut conn, &pepper.hash(&code), ttl).await?;

    let open: Vec<_> = invites::list(&mut conn)
        .await?
        .into_iter()
        .map(|invite| invite.code_hash)
        .collect();

    Ok(Created {
        code,
        invite: Invite::new(&stored, &open),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::count;

    const TTL: Duration = Duration::from_hours(24);

    fn pepper() -> InvitePepper {
        InvitePepper::new([1; 32])
    }

    #[sqlx::test]
    async fn stores_the_peppered_hash_of_the_code(db: PgPool) {
        let created = create(&db, &pepper(), TTL).await.unwrap();

        let stored: Vec<u8> = sqlx::query_scalar("select code_hash from invites")
            .fetch_one(&db)
            .await
            .unwrap();
        assert_eq!(stored, pepper().hash(&created.code).as_bytes());
    }

    #[sqlx::test]
    async fn every_invite_gets_its_own_code(db: PgPool) {
        let a = create(&db, &pepper(), TTL).await.unwrap();
        let b = create(&db, &pepper(), TTL).await.unwrap();

        assert!(a.code != b.code);
        assert_eq!(count(&db, "invites").await, 2);
    }

    #[sqlx::test]
    async fn id_is_a_prefix_of_the_stored_hash(db: PgPool) {
        let created = create(&db, &pepper(), TTL).await.unwrap();

        let hash = pepper().hash(&created.code).to_string();
        let id = created.invite.id.to_string();
        assert!(hash.starts_with(&id));
        assert_eq!(id.len(), 5);
    }

    #[sqlx::test]
    async fn expiry_is_ttl_after_creation(db: PgPool) {
        let created = create(&db, &pepper(), TTL).await.unwrap();

        assert_eq!(created.invite.expires_at - created.invite.created_at, TTL);
    }
}
