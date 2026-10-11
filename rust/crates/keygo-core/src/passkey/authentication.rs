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
    // TODO passkey-rs's Option store does not filter by rp_id yet (see its TODO); drop this once it
    // does and refuses_to_sign_for_another_relying_party still passes without it
    if options.rp_id.as_deref() != Some(passkey.rp_id.as_str()) {
        return Err(PasskeyError::RpMismatch);
    }
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::passkey::registration::register;

    const REGISTRATION: &str = r#"{
        "rp": { "id": "example.com", "name": "Example Corp" },
        "user": { "id": "AQIDBA", "name": "alice@example.com", "displayName": "Alice" },
        "challenge": "AAECAwQFBgcICQoLDA0ODw",
        "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }]
    }"#;

    fn assertion_request(rp_id: &str) -> String {
        format!(r#"{{ "challenge": "AAECAwQFBgcICQoLDA0ODw", "rpId": "{rp_id}" }}"#)
    }

    #[tokio::test]
    async fn signs_for_the_relying_party_it_was_registered_with() {
        let stored = register(REGISTRATION).await.unwrap().private_key;

        assert!(
            authenticate(&assertion_request("example.com"), &stored, None)
                .await
                .is_ok()
        );
    }

    #[tokio::test]
    async fn refuses_to_sign_for_another_relying_party() {
        let stored = register(REGISTRATION).await.unwrap().private_key;

        let result = authenticate(&assertion_request("evil.com"), &stored, None).await;

        assert!(
            matches!(result, Err(PasskeyError::RpMismatch)),
            "{result:?}"
        );
    }
}
