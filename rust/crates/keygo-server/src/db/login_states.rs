use super::corrupt;
use crate::domain::LoginId;
use keygo_opaque::server::ServerLogin;
use sqlx::PgConnection;
use std::time::Duration;
use uuid::Uuid;
use zeroize::Zeroizing;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Purpose {
    Login,
}

impl Purpose {
    const fn as_str(self) -> &'static str {
        match self {
            Self::Login => "login",
        }
    }
}

pub async fn insert(
    conn: &mut PgConnection,
    login_id: &LoginId,
    user_id: Option<Uuid>,
    purpose: Purpose,
    state: &ServerLogin,
    ttl: Duration,
) -> sqlx::Result<()> {
    let state = state.to_bytes();
    sqlx::query!(
        "INSERT INTO login_states (id, user_id, purpose, state, expires_at)
         VALUES ($1, $2, $3, $4, now() + make_interval(secs => $5))",
        login_id.as_bytes().as_slice(),
        user_id,
        purpose.as_str(),
        state.as_slice(),
        ttl.as_secs_f64(),
    )
    .execute(conn)
    .await?;

    Ok(())
}

pub struct Pending {
    /// `None` on the fake path for an unknown username.
    pub user_id: Option<Uuid>,
    pub state: ServerLogin,
}

/// Deletes the state as it reads it, so a login can be finished at most once. Returns `None` for
/// an unknown or expired login.
pub async fn take(
    conn: &mut PgConnection,
    login_id: &LoginId,
    purpose: Purpose,
) -> sqlx::Result<Option<Pending>> {
    let Some(row) = sqlx::query!(
        "DELETE FROM login_states
         WHERE id = $1 AND purpose = $2 AND expires_at > now()
         RETURNING user_id, state",
        login_id.as_bytes().as_slice(),
        purpose.as_str(),
    )
    .fetch_optional(conn)
    .await?
    else {
        return Ok(None);
    };

    let state = Zeroizing::new(row.state);
    let state = ServerLogin::from_bytes(&state).map_err(corrupt)?;

    Ok(Some(Pending {
        user_id: row.user_id,
        state,
    }))
}

pub async fn delete_expired(conn: &mut PgConnection) -> sqlx::Result<u64> {
    let result = sqlx::query!("DELETE FROM login_states WHERE expires_at <= now()")
        .execute(conn)
        .await?;

    Ok(result.rows_affected())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::{count, expire, expires_in_secs};
    use keygo_opaque::client::ClientLogin;
    use keygo_opaque::server::ServerSetup;
    use sqlx::PgPool;

    const TTL: Duration = Duration::from_mins(1);

    fn server_login() -> ServerLogin {
        let (_, request) = ClientLogin::start(b"password").unwrap();
        let (state, _) =
            ServerLogin::start(&ServerSetup::generate(), None, request, b"identifier").unwrap();
        state
    }

    async fn insert_new(conn: &mut PgConnection, user_id: Option<Uuid>) -> LoginId {
        let login_id = LoginId::random();
        insert(
            conn,
            &login_id,
            user_id,
            Purpose::Login,
            &server_login(),
            TTL,
        )
        .await
        .unwrap();
        login_id
    }

    #[sqlx::test]
    async fn state_is_taken_once(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let user_id = Uuid::new_v4();
        let login_id = insert_new(&mut conn, Some(user_id)).await;

        let pending = take(&mut conn, &login_id, Purpose::Login)
            .await
            .unwrap()
            .unwrap();

        assert_eq!(pending.user_id, Some(user_id));
        assert!(
            take(&mut conn, &login_id, Purpose::Login)
                .await
                .unwrap()
                .is_none()
        );
        assert_eq!(count(&db, "login_states").await, 0);
    }

    #[sqlx::test]
    async fn state_round_trips_its_bytes(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let state = server_login();
        let login_id = LoginId::random();
        insert(&mut conn, &login_id, None, Purpose::Login, &state, TTL)
            .await
            .unwrap();

        let pending = take(&mut conn, &login_id, Purpose::Login)
            .await
            .unwrap()
            .unwrap();

        assert_eq!(*pending.state.to_bytes(), *state.to_bytes());
    }

    #[sqlx::test]
    async fn fake_path_stores_no_user(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let login_id = insert_new(&mut conn, None).await;

        let pending = take(&mut conn, &login_id, Purpose::Login)
            .await
            .unwrap()
            .unwrap();

        assert_eq!(pending.user_id, None);
    }

    #[sqlx::test]
    async fn unknown_login_is_none(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        insert_new(&mut conn, None).await;

        let pending = take(&mut conn, &LoginId::random(), Purpose::Login)
            .await
            .unwrap();

        assert!(pending.is_none());
        assert_eq!(count(&db, "login_states").await, 1);
    }

    #[sqlx::test]
    async fn expired_login_is_none(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        let login_id = insert_new(&mut conn, None).await;
        expire(&db, "login_states").await;

        assert!(
            take(&mut conn, &login_id, Purpose::Login)
                .await
                .unwrap()
                .is_none()
        );
    }

    #[sqlx::test]
    async fn delete_expired_keeps_live_states(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        insert_new(&mut conn, None).await;
        insert_new(&mut conn, Some(Uuid::new_v4())).await;
        expire(&db, "login_states").await;
        let live = insert_new(&mut conn, None).await;

        assert_eq!(delete_expired(&mut conn).await.unwrap(), 2);
        assert_eq!(count(&db, "login_states").await, 1);
        assert!(
            take(&mut conn, &live, Purpose::Login)
                .await
                .unwrap()
                .is_some()
        );
    }

    #[sqlx::test]
    async fn state_expires_after_ttl(db: PgPool) {
        let mut conn = db.acquire().await.unwrap();
        insert_new(&mut conn, None).await;

        let remaining = expires_in_secs(&db, "login_states").await;

        assert!(
            (TTL.as_secs_f64() - 5.0..=TTL.as_secs_f64()).contains(&remaining),
            "{remaining}"
        );
    }

    #[sqlx::test]
    async fn state_of_another_purpose_is_not_taken(db: PgPool) {
        let login_id = LoginId::random();
        sqlx::query(
            "INSERT INTO login_states (id, purpose, state, expires_at)
             VALUES ($1, 'step_up', $2, now() + interval '1 minute')",
        )
        .bind(login_id.as_bytes().as_slice())
        .bind(server_login().to_bytes().as_slice())
        .execute(&db)
        .await
        .unwrap();
        let mut conn = db.acquire().await.unwrap();

        assert!(
            take(&mut conn, &login_id, Purpose::Login)
                .await
                .unwrap()
                .is_none()
        );
        assert_eq!(count(&db, "login_states").await, 1);
    }

    #[sqlx::test]
    async fn undecodable_state_is_corrupt(db: PgPool) {
        let login_id = LoginId::random();
        sqlx::query(
            "INSERT INTO login_states (id, purpose, state, expires_at)
             VALUES ($1, 'login', '\\x00', now() + interval '1 minute')",
        )
        .bind(login_id.as_bytes().as_slice())
        .execute(&db)
        .await
        .unwrap();
        let mut conn = db.acquire().await.unwrap();

        let err = take(&mut conn, &login_id, Purpose::Login)
            .await
            .err()
            .unwrap();

        assert!(matches!(err, sqlx::Error::Decode(_)));
    }
}
