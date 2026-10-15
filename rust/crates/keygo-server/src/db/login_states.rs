use crate::domain::LoginId;
use keygo_opaque::server::ServerLogin;
use sqlx::PgConnection;
use std::time::Duration;
use thiserror::Error;
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

#[derive(Debug, Error)]
pub enum TakeError {
    #[error("login expired or unknown")]
    Expired,
    #[error("database error: {0}")]
    Db(#[from] sqlx::Error),
}

pub struct Taken {
    /// `None` on the fake path for an unknown username.
    pub user_id: Option<Uuid>,
    pub state: ServerLogin,
}

/// Deletes the state as it reads it, so a login can be finished at most once.
pub async fn take(
    conn: &mut PgConnection,
    login_id: &LoginId,
    purpose: Purpose,
) -> Result<Taken, TakeError> {
    let row = sqlx::query!(
        "DELETE FROM login_states
         WHERE id = $1 AND purpose = $2 AND expires_at > now()
         RETURNING user_id, state",
        login_id.as_bytes().as_slice(),
        purpose.as_str(),
    )
    .fetch_optional(conn)
    .await?
    .ok_or(TakeError::Expired)?;

    let state = Zeroizing::new(row.state);
    // `insert` only stores serialized states, so this fails only on a corrupted row.
    let state =
        ServerLogin::from_bytes(&state).map_err(|err| sqlx::Error::Decode(Box::new(err)))?;

    Ok(Taken {
        user_id: row.user_id,
        state,
    })
}
