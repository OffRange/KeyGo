use crate::passkey::authenticator::authenticator;
use crate::passkey::{PasskeyError, codec, origin, parse_request, to_json};
use passkey::client::Client;
use passkey::types::webauthn::{CredentialRequestOptions, PublicKeyCredentialRequestOptions};

pub async fn authenticate(
    request_json: &str,
    stored_passkey: &[u8],
    client_data_hash: Option<Vec<u8>>,
) -> Result<String, PasskeyError> {
    let options: PublicKeyCredentialRequestOptions = parse_request(request_json)?;
    let passkey = codec::decode(stored_passkey)?;
    let origin = origin(options.rp_id.as_deref())?;

    let mut client = Client::new(authenticator(Some(passkey)));
    let credential = client
        .authenticate(
            origin,
            CredentialRequestOptions {
                public_key: options,
            },
            client_data_hash,
        )
        .await
        .map_err(PasskeyError::Webauthn)?;

    to_json(&credential)
}
