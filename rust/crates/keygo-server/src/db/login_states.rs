use crate::domain::LoginId;
use keygo_opaque::server::ServerLogin;
use sqlx::PgConnection;
use std::time::Duration;
use uuid::Uuid;

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
