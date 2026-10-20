use keygo_opaque::client::{ClientLogin, ClientRegistration, LoginFinish};
use keygo_opaque::server::{self, ServerLogin, ServerSetup};
use keygo_opaque::{
    CredentialFinalization, CredentialRequest, CredentialResponse, OpaqueError, PasswordFile,
    RegistrationRequest,
};

const ID: &[u8] = b"account-1";
const PASSWORD: &[u8] = b"correct horse battery staple";

fn register(setup: &ServerSetup, password: &[u8]) -> (PasswordFile, Vec<u8>) {
    let (client, request) = ClientRegistration::start(password).unwrap();
    let request = RegistrationRequest::from_bytes(&request.to_bytes()).unwrap();
    let response = server::registration_start(setup, request, ID).unwrap();
    let finish = client.finish(password, response).unwrap();
    let file = server::registration_finish(finish.upload);
    let file = PasswordFile::from_bytes(&file.to_bytes()).unwrap();
    (file, finish.export_key.as_bytes().to_vec())
}

fn login(
    setup: &ServerSetup,
    file: Option<PasswordFile>,
    password: &[u8],
) -> Result<(LoginFinish, ServerLogin), OpaqueError> {
    let (client, request) = ClientLogin::start(password)?;
    let request = CredentialRequest::from_bytes(&request.to_bytes())?;
    let (server, response) = ServerLogin::start(setup, file, request, ID)?;
    let server = ServerLogin::from_bytes(&server.to_bytes())?;
    let response = CredentialResponse::from_bytes(&response.to_bytes())?;
    Ok((client.finish(password, response)?, server))
}

#[test]
fn register_then_login_agrees_on_keys() {
    let setup = ServerSetup::generate();
    let (file, registration_export_key) = register(&setup, PASSWORD);

    let (client, server) = login(&setup, Some(file), PASSWORD).unwrap();
    let finalization = CredentialFinalization::from_bytes(&client.finalization.to_bytes()).unwrap();
    let server_session_key = server.finish(finalization).unwrap();

    assert_eq!(client.session_key.as_bytes(), server_session_key.as_bytes());
    assert_eq!(
        client.export_key.as_bytes().as_slice(),
        registration_export_key
    );
    assert_eq!(client.server_public_key, setup.public_key());
}

#[test]
fn wrong_password_is_rejected_by_the_client() {
    let setup = ServerSetup::generate();
    let (file, _) = register(&setup, PASSWORD);

    assert_eq!(
        login(&setup, Some(file), b"wrong").err(),
        Some(OpaqueError::InvalidLogin),
    );
}

#[test]
fn unknown_account_fails_like_a_wrong_password() {
    let setup = ServerSetup::generate();

    assert_eq!(
        login(&setup, None, PASSWORD).err(),
        Some(OpaqueError::InvalidLogin),
    );
}

#[test]
fn login_against_a_different_server_setup_fails() {
    let (file, _) = register(&ServerSetup::generate(), PASSWORD);

    assert_eq!(
        login(&ServerSetup::generate(), Some(file), PASSWORD).err(),
        Some(OpaqueError::InvalidLogin),
    );
}

#[test]
fn server_setup_survives_serialization() {
    let setup = ServerSetup::generate();
    let restored = ServerSetup::from_bytes(&setup.to_bytes()).unwrap();
    let (file, _) = register(&setup, PASSWORD);

    assert_eq!(restored.public_key(), setup.public_key());
    assert!(login(&restored, Some(file), PASSWORD).is_ok());
}

#[test]
fn empty_password_is_rejected() {
    assert_eq!(
        ClientRegistration::start(b"").err(),
        Some(OpaqueError::EmptyPassword)
    );
    assert_eq!(
        ClientLogin::start(b"").err(),
        Some(OpaqueError::EmptyPassword)
    );
}

#[test]
fn malformed_bytes_are_reported_as_malformed() {
    assert_eq!(
        RegistrationRequest::from_bytes(&[]).err(),
        Some(OpaqueError::Malformed)
    );
    assert_eq!(
        CredentialRequest::from_bytes(&[0xff; 7]).err(),
        Some(OpaqueError::Malformed)
    );
    assert_eq!(
        PasswordFile::from_bytes(&[0; 3]).err(),
        Some(OpaqueError::Malformed)
    );
}
