use crate::db::{invites, registrations, sessions};
use crate::service::session::SESSION_RETENTION;
use sqlx::PgPool;
use std::time::Duration;
use tokio::time::MissedTickBehavior;

/// How often expired rows are deleted. Expired rows are never read, since every `take` filters on
/// `expires_at`, so this bounds only how long they occupy the table, not how long they are valid.
const INTERVAL: Duration = Duration::from_mins(1);

/// Deletes expired registrations and invites, and sessions past their retention, until the task is
/// aborted. A start that is never finished (a wrong password fails at the client, which then never
/// calls `finish`) leaves its row behind, and every refresh adds a token row, so without this the
/// tables would only grow.
pub async fn run(db: PgPool) {
    let mut interval = tokio::time::interval(INTERVAL);
    interval.set_missed_tick_behavior(MissedTickBehavior::Delay);

    loop {
        interval.tick().await;
        if let Err(err) = sweep(&db).await {
            tracing::warn!("sweeping expired rows failed: {err:#}");
        }
    }
}

async fn sweep(db: &PgPool) -> sqlx::Result<()> {
    let mut conn = db.acquire().await?;
    let registrations = registrations::delete_expired(&mut conn).await?;
    let invites = invites::delete_expired(&mut conn).await?;
    let sessions = sessions::delete_ended(&mut conn, SESSION_RETENTION).await?;
    tracing::debug!(registrations, invites, sessions, "swept expired rows");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::InviteHash;
    use crate::service::registration::{self, Signup, StartRegistration};
    use crate::testing::{count, expire};
    use keygo_opaque::client::ClientRegistration;
    use keygo_opaque::server::ServerSetup;
    use uuid::Uuid;

    async fn start_registration(db: &PgPool, setup: &ServerSetup, name: &str) {
        let (_, registration_request) = ClientRegistration::start(b"password").unwrap();
        let input = StartRegistration {
            username: crate::testing::username(name),
            user_id: Uuid::new_v4(),
            invite: None,
            registration_request,
        };
        registration::start(db, setup, &Signup::Open, input)
            .await
            .unwrap();
    }

    async fn create_invite(db: &PgPool, byte: u8) {
        let mut conn = db.acquire().await.unwrap();
        invites::insert(
            &mut conn,
            &InviteHash::from([byte; 32]),
            Duration::from_hours(24),
        )
        .await
        .unwrap();
    }

    #[sqlx::test]
    async fn sweep_deletes_only_expired_rows(db: PgPool) {
        let setup = ServerSetup::generate();
        start_registration(&db, &setup, "ada").await;
        create_invite(&db, 1).await;
        expire(&db, "pending_registrations").await;
        expire(&db, "invites").await;
        start_registration(&db, &setup, "bob").await;
        create_invite(&db, 2).await;

        sweep(&db).await.unwrap();

        assert_eq!(count(&db, "pending_registrations").await, 1);
        assert_eq!(count(&db, "invites").await, 1);
    }

    #[sqlx::test]
    async fn sweep_of_empty_tables_is_a_no_op(db: PgPool) {
        sweep(&db).await.unwrap();

        assert_eq!(count(&db, "pending_registrations").await, 0);
        assert_eq!(count(&db, "invites").await, 0);
    }
}
