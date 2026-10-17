use super::Invite;
use crate::db::invites;
use crate::domain::InviteId;
use sqlx::PgPool;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum RevokeError {
    #[error("no open invite matches {0}")]
    NotFound(InviteId),
    #[error("{id} matches {} open invites, nothing revoked", matches.len())]
    Ambiguous { id: InviteId, matches: Vec<Invite> },
    #[error("database error: {0}")]
    Db(#[from] sqlx::Error),
}

pub async fn revoke(db: &PgPool, invite_id: &InviteId) -> Result<(), RevokeError> {
    let not_found = || RevokeError::NotFound(invite_id.clone());

    let mut tx = db.begin().await?;
    let matches = invites::find_by_id(&mut tx, invite_id).await?;
    let hash = match matches.as_slice() {
        [] => return Err(not_found()),
        [invite] => invite.code_hash,
        _ => {
            return Err(RevokeError::Ambiguous {
                id: invite_id.clone(),
                matches: Invite::all(&matches),
            });
        }
    };

    if invites::delete(&mut tx, &hash).await? == 0 {
        return Err(not_found());
    }
    tx.commit().await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::InviteHash;
    use crate::testing::{count, expire, insert_invite as insert, invite_hash as hash};

    fn id(s: &str) -> InviteId {
        s.parse().unwrap()
    }

    async fn open_hashes(db: &PgPool) -> Vec<InviteHash> {
        let mut conn = db.acquire().await.unwrap();
        invites::list(&mut conn)
            .await
            .unwrap()
            .into_iter()
            .map(|invite| invite.code_hash)
            .collect()
    }

    #[sqlx::test]
    async fn deletes_the_matching_invite_only(db: PgPool) {
        insert(&db, hash(&[0xa3, 0xf9, 0xc1, 0x70])).await;
        insert(&db, hash(&[0xa3, 0xf9, 0xc1, 0xe0])).await;

        revoke(&db, &id("a3f9c17")).await.unwrap();

        assert_eq!(open_hashes(&db).await, [hash(&[0xa3, 0xf9, 0xc1, 0xe0])]);
    }

    #[sqlx::test]
    async fn full_hash_revokes(db: PgPool) {
        let full = hash(&[0xa3, 0xf9, 0xc1, 0x70]);
        insert(&db, full).await;

        revoke(&db, &id(&full.to_string())).await.unwrap();

        assert_eq!(count(&db, "invites").await, 0);
    }

    #[sqlx::test]
    async fn shared_prefix_is_ambiguous(db: PgPool) {
        insert(&db, hash(&[0xa3, 0xf9, 0xc1, 0x70])).await;
        insert(&db, hash(&[0xa3, 0xf9, 0xc1, 0xe0])).await;

        let err = revoke(&db, &id("a3f9c")).await.unwrap_err();

        assert!(matches!(err, RevokeError::Ambiguous { .. }));
        assert_eq!(count(&db, "invites").await, 2);
    }

    #[sqlx::test]
    async fn expired_invite_does_not_make_an_id_ambiguous(db: PgPool) {
        insert(&db, hash(&[0xa3, 0xf9, 0xc1, 0xe0])).await;
        expire(&db, "invites").await;
        insert(&db, hash(&[0xa3, 0xf9, 0xc1, 0x70])).await;

        revoke(&db, &id("a3f9c")).await.unwrap();

        assert!(open_hashes(&db).await.is_empty());
        assert_eq!(count(&db, "invites").await, 1);
    }

    #[sqlx::test]
    async fn unknown_id_is_not_found(db: PgPool) {
        insert(&db, hash(&[0x11])).await;

        let err = revoke(&db, &id("22000")).await.unwrap_err();

        assert!(matches!(err, RevokeError::NotFound(_)));
        assert_eq!(count(&db, "invites").await, 1);
    }

    #[sqlx::test]
    async fn expired_invite_is_not_found(db: PgPool) {
        insert(&db, hash(&[0x11])).await;
        expire(&db, "invites").await;

        let err = revoke(&db, &id("11000")).await.unwrap_err();

        assert!(matches!(err, RevokeError::NotFound(_)));
        assert_eq!(count(&db, "invites").await, 1);
    }
}
