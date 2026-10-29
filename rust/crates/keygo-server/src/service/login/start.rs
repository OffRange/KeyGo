use crate::cache::{LoginStates, Pending};
use crate::db::opaque_records;
use crate::domain::{LoginId, Username};
use crate::service::login::{Error, TTL};
use keygo_opaque::server::{ServerLogin, ServerSetup};
use keygo_opaque::{CredentialRequest, CredentialResponse};
use sqlx::PgPool;
use std::time::Duration;

pub struct StartLogin {
    pub username: Username,
    pub credential_request: CredentialRequest,
}

pub struct Started {
    pub login_id: LoginId,
    pub credential_response: CredentialResponse,
    pub expires_in: Duration,
}

/// Answers known and unknown usernames alike: an unknown one runs OPAQUE against a fake record
/// whose identifier is stable per name, so the client fails at `finish` exactly as for a wrong
/// password and the response never says whether the account exists. The identifier needs no
/// secret of its own: it only feeds the OPRF key derivation, which is keyed by the secret seed in
/// `ServerSetup`.
pub async fn start(
    db: &PgPool,
    login_states: &LoginStates,
    setup: &ServerSetup,
    input: StartLogin,
) -> Result<Started, Error> {
    let StartLogin {
        username,
        credential_request,
    } = input;

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

    let (state, credential_response) =
        ServerLogin::start(setup, password_file, credential_request, &identifier)?;

    let login_id = LoginId::random();
    login_states.insert(login_id, Pending { user_id, state });

    Ok(Started {
        login_id,
        credential_response,
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
    use crate::testing::{register, start_login};

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

    #[sqlx::test]
    async fn start_stores_state_for_a_known_user(db: PgPool) {
        let login_states = LoginStates::new();
        let setup = ServerSetup::generate();
        let user_id = register(&db, &setup, "ada").await;

        let (_, started) = start_login(&db, &login_states, &setup, "ada").await;

        assert_eq!(started.expires_in, TTL);
        let pending = login_states.take(&started.login_id).unwrap();
        assert_eq!(pending.user_id, Some(user_id));
    }

    #[sqlx::test]
    async fn start_answers_an_unknown_user_without_one(db: PgPool) {
        let login_states = LoginStates::new();

        let (_, started) = start_login(&db, &login_states, &ServerSetup::generate(), "ada").await;

        assert_eq!(started.expires_in, TTL);
        let pending = login_states.take(&started.login_id).unwrap();
        assert_eq!(pending.user_id, None);
    }

    #[sqlx::test]
    async fn every_start_is_a_separate_login(db: PgPool) {
        let login_states = LoginStates::new();
        let setup = ServerSetup::generate();

        let (_, first) = start_login(&db, &login_states, &setup, "ada").await;
        let (_, second) = start_login(&db, &login_states, &setup, "ada").await;

        assert_ne!(first.login_id, second.login_id);
        assert_eq!(login_states.count(), 2);
    }
}
