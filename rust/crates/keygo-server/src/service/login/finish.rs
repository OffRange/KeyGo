use crate::db::login_states::{self, Purpose, TakeError};
use crate::db::users;
use crate::domain::{LoginId, Username};
use keygo_opaque::{CredentialFinalization, OpaqueError};
use sqlx::PgPool;
use thiserror::Error;
use uuid::Uuid;

#[derive(Debug, Error)]
pub enum FinishError {
    #[error("login expired or unknown")]
    Expired,
    #[error(transparent)]
    Opaque(#[from] OpaqueError),
    #[error("database error: {0}")]
    Db(#[from] sqlx::Error),
}

impl From<TakeError> for FinishError {
    fn from(err: TakeError) -> Self {
        match err {
            TakeError::Expired => Self::Expired,
            TakeError::Db(err) => Self::Db(err),
        }
    }
}

pub struct FinishLogin {
    pub login_id: LoginId,
    pub credential_finalization: CredentialFinalization,
}

pub struct Finished {
    pub user_id: Uuid,
    pub username: Username,
    // TODO: session
}

/// The state is consumed before verifying, so a failed finish cannot be retried with the same
/// `login_id`: every password guess costs a fresh `start`.
pub async fn finish(db: &PgPool, input: FinishLogin) -> Result<Finished, FinishError> {
    let FinishLogin {
        login_id,
        credential_finalization,
    } = input;

    let mut conn = db.acquire().await?;
    let taken = login_states::take(&mut conn, &login_id, Purpose::Login).await?;

    // TODO: derive the session from the session key once sessions exist.
    let _session_key = taken.state.finish(credential_finalization)?;

    // The fake path cannot produce a valid finalization, and an account deleted since `start` has
    // no user left to log into; both answer exactly like a wrong password.
    let user_id = taken.user_id.ok_or(OpaqueError::InvalidLogin)?;
    let username = users::find_username(&mut conn, user_id)
        .await?
        .ok_or(OpaqueError::InvalidLogin)?;

    Ok(Finished { user_id, username })
}
