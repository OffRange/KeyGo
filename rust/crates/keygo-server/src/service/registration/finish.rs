use crate::db::registrations::{self, ConsumeError};
use crate::db::{opaque_records, users};
use crate::domain::{RegistrationId, Username};
use keygo_opaque::{RegistrationUpload, server};
use sqlx::PgPool;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum FinishError {
    #[error("registration expired or unknown")]
    Expired,
    #[error("database error: {0}")]
    Db(#[from] sqlx::Error),
}

impl From<ConsumeError> for FinishError {
    fn from(err: ConsumeError) -> Self {
        match err {
            ConsumeError::Expired => Self::Expired,
            ConsumeError::Db(err) => Self::Db(err),
        }
    }
}

pub struct FinishRegistration {
    pub registration_id: RegistrationId,
    pub registration_upload: RegistrationUpload,
    // TODO: key bundle + device info
}

pub struct Finished {
    pub username: Username,
    // TODO: session
}

pub async fn finish(db: &PgPool, input: FinishRegistration) -> Result<Finished, FinishError> {
    let FinishRegistration {
        registration_id,
        registration_upload,
    } = input;

    // Pure computation, so it runs before the transaction to keep the reservation's lock short.
    let password_file = server::registration_finish(registration_upload);

    let mut tx = db.begin().await?;
    let pending = registrations::consume(&mut tx, &registration_id).await?;
    users::insert(&mut tx, pending.user_id, &pending.username).await?;
    opaque_records::insert(&mut tx, pending.user_id, &password_file).await?;
    tx.commit().await?;

    Ok(Finished {
        username: pending.username,
    })
}
