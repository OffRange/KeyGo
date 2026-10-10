use crate::passkey::authenticator::authenticator;
use crate::passkey::{PasskeyError, codec, origin, parse_request, to_json};
use passkey::client::{Client, DefaultClientData};
use passkey::types::webauthn::{CredentialCreationOptions, PublicKeyCredentialCreationOptions};

/// A newly created passkey: the response JSON for the relying party, and what to store.
pub struct Registration {
    pub response: String,
    pub user_name: String,
    pub user_display_name: String,
    pub credential_id: Vec<u8>,
    /// The passkey in its stored encoding.
    pub private_key: Vec<u8>,
    pub rp: String,
}

/// What the app needs from a creation request before asking the user to confirm it.
pub struct CreationInfo {
    pub exclude_credentials: Vec<Vec<u8>>,
    pub rp: String,
}

pub fn creation_info(request_json: &str) -> Result<CreationInfo, PasskeyError> {
    let options: PublicKeyCredentialCreationOptions = parse_request(request_json)?;
    Ok(CreationInfo {
        exclude_credentials: options
            .exclude_credentials
            .unwrap_or_default()
            .into_iter()
            .map(|descriptor| descriptor.id.into())
            .collect(),
        rp: options.rp.id.unwrap_or_default(),
    })
}

pub async fn register(request_json: &str) -> Result<Registration, PasskeyError> {
    let options: PublicKeyCredentialCreationOptions = parse_request(request_json)?;
    let origin = origin(options.rp.id.as_deref())?;
    let user_name = options.user.name.clone();
    let user_display_name = options.user.display_name.clone();

    let mut client = Client::new(authenticator(None));
    let credential = client
        .register(
            origin,
            CredentialCreationOptions {
                public_key: options,
            },
            DefaultClientData,
        )
        .await
        .map_err(PasskeyError::Webauthn)?;

    let passkey = client
        .authenticator()
        .store()
        .clone()
        .ok_or(PasskeyError::NoCredentialCreated)?;

    Ok(Registration {
        response: to_json(&credential)?,
        user_name,
        user_display_name,
        credential_id: passkey.credential_id.to_vec(),
        rp: passkey.rp_id.clone(),
        private_key: codec::encode(passkey)?,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    const REQUEST: &str = r#"{
        "rp": { "id": "example.com", "name": "Example Corp" },
        "user": { "id": "AQIDBA", "name": "alice@example.com", "displayName": "Alice" },
        "challenge": "AAECAwQFBgcICQoLDA0ODw",
        "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }]
    }"#;

    #[tokio::test]
    async fn registration_reports_the_users_name_not_the_relying_partys() {
        let registration = register(REQUEST).await.unwrap();

        assert_eq!(registration.user_name, "alice@example.com");
        assert_eq!(registration.user_display_name, "Alice");
        assert_eq!(registration.rp, "example.com");
    }
}
