use crate::db::login_states::{self, Purpose};
use crate::db::opaque_records;
use crate::domain::{LoginId, Username};
use crate::service::login::TTL;
use keygo_opaque::server::{ServerLogin, ServerSetup};
use keygo_opaque::{CredentialRequest, CredentialResponse, OpaqueError};
use sqlx::PgPool;
use std::time::Duration;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum StartError {
    #[error(transparent)]
    Opaque(#[from] OpaqueError),
    #[error("database error: {0}")]
    Db(#[from] sqlx::Error),
}

pub struct StartLogin {
    pub username: Username,
    pub request: CredentialRequest,
}

pub struct Started {
    pub login_id: LoginId,
    pub response: CredentialResponse,
    pub expires_in: Duration,
}

/// Answers known and unknown usernames alike: an unknown one runs OPAQUE against a fake record
/// whose identifier is stable per name, so the client fails at `finish` exactly as for a wrong
/// password and the response never says whether the account exists. The identifier needs no
/// secret of its own: it only feeds the OPRF key derivation, which is keyed by the secret seed in
/// `ServerSetup`.
pub async fn start(
    db: &PgPool,
    setup: &ServerSetup,
    input: StartLogin,
) -> Result<Started, StartError> {
    let StartLogin { username, request } = input;
    // TODO: rate-limit starts per account

    let mut conn = db.acquire().await?;
    let record = opaque_records::find_by_username(&mut conn, &username).await?;

    let (user_id, password_file, identifier) = match record {
        Some(record) => (
            Some(record.user_id),
            Some(record.file),
            record.user_id.as_bytes().to_vec(),
        ),
        None => (None, None, fake_identifier(&username)),
    };

    let (state, response) = ServerLogin::start(setup, password_file, request, &identifier)?;

    let login_id = LoginId::random();
    login_states::insert(&mut conn, &login_id, user_id, Purpose::Login, &state, TTL).await?;

    Ok(Started {
        login_id,
        response,
        expires_in: TTL,
    })
}

/// The domain tag keeps a fake identifier from ever encoding like a real one, a 16-byte user id.
/// Usernames never contain NUL, so the separator keeps tag and name unambiguous.
fn fake_identifier(username: &Username) -> Vec<u8> {
    [b"v1:fake/login\0".as_slice(), username.as_str().as_bytes()].concat()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn name(raw: &str) -> Username {
        Username::parse(raw).unwrap()
    }

    #[test]
    fn fake_identifier_is_stable_per_username() {
        assert_eq!(fake_identifier(&name("ada")), fake_identifier(&name("ADA")));
        assert_ne!(fake_identifier(&name("ada")), fake_identifier(&name("bob")));
    }

    #[test]
    fn fake_identifier_is_pinned() {
        assert_eq!(fake_identifier(&name("ada")), b"v1:fake/login\0ada");
    }
}
