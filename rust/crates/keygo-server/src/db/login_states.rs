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
