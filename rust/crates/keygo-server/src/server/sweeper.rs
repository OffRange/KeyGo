use crate::db::{login_states, registrations};
use sqlx::PgPool;
use std::time::Duration;
use tokio::time::MissedTickBehavior;

/// How often expired rows are deleted. Expired rows are never read, since every `take` filters on
/// `expires_at`, so this bounds only how long they occupy the table, not how long they are valid.
const INTERVAL: Duration = Duration::from_mins(1);

/// Deletes expired registrations and login states until the task is aborted. A start that is
/// never finished (a wrong password fails at the client, which then never calls `finish`) leaves
/// its row behind, so without this the tables would only grow.
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
    let logins = login_states::delete_expired(&mut conn).await?;
    tracing::debug!(registrations, logins, "swept expired rows");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::service::registration::{self, StartRegistration};
    use crate::testing::{count, expire, start_login};
    use keygo_opaque::client::ClientRegistration;
    use keygo_opaque::server::ServerSetup;
    use uuid::Uuid;

    async fn start_registration(db: &PgPool, setup: &ServerSetup, name: &str) {
        let (_, registration_request) = ClientRegistration::start(b"password").unwrap();
        let input = StartRegistration {
            username: crate::testing::username(name),
            user_id: Uuid::new_v4(),
            registration_request,
        };
        registration::start(db, setup, input).await.unwrap();
    }

    #[sqlx::test]
    async fn sweep_deletes_only_expired_rows(db: PgPool) {
        let setup = ServerSetup::generate();
        start_registration(&db, &setup, "ada").await;
        start_login(&db, &setup, "ada").await;
        expire(&db, "pending_registrations").await;
        expire(&db, "login_states").await;
        start_registration(&db, &setup, "bob").await;
        start_login(&db, &setup, "bob").await;

        sweep(&db).await.unwrap();

        assert_eq!(count(&db, "pending_registrations").await, 1);
        assert_eq!(count(&db, "login_states").await, 1);
    }

    #[sqlx::test]
    async fn sweep_of_empty_tables_is_a_no_op(db: PgPool) {
        sweep(&db).await.unwrap();

        assert_eq!(count(&db, "pending_registrations").await, 0);
        assert_eq!(count(&db, "login_states").await, 0);
    }
}
