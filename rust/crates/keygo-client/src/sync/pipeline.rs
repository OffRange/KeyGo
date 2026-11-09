use super::client::SyncClient;
use super::error::{SyncError, SyncResult, from_response};
use super::transport::{Header, HttpRequest, HttpResponse};
use keygo_api::{ApiError, Auth, Endpoint, ErrorCode, Method};
use keygo_dpop::{Htm, ProofRequest};
use serde::Serialize;
use serde::de::DeserializeOwned;

/// A serialized `null` (the `()` body) is sent as no body at all.
fn encode_body<T: Serialize>(value: &T) -> SyncResult<Option<Vec<u8>>> {
    let json = serde_json::to_value(value).map_err(|e| SyncError::Client {
        message: format!("request does not serialize: {e}"),
    })?;
    if json.is_null() {
        return Ok(None);
    }
    serde_json::to_vec(&json)
        .map(Some)
        .map_err(|e| SyncError::Client {
            message: format!("request does not serialize: {e}"),
        })
}

/// An empty body decodes as `null`, so `()` accepts a 204.
fn decode_body<T: DeserializeOwned>(body: &[u8]) -> SyncResult<T> {
    let body = if body.is_empty() {
        b"null".as_slice()
    } else {
        body
    };
    serde_json::from_slice(body).map_err(|e| SyncError::invalid_response(e.to_string()))
}

fn proof_method(method: Method) -> Htm {
    match method {
        Method::Get => Htm::Get,
        Method::Post => Htm::Post,
        Method::Delete => Htm::Delete,
    }
}

fn header_value<'a>(response: &'a HttpResponse, name: &str) -> Option<&'a str> {
    response
        .headers
        .iter()
        .find(|h| h.name.eq_ignore_ascii_case(name))
        .map(|h| h.value.as_str())
}

fn is_nonce_challenge(response: &HttpResponse) -> bool {
    serde_json::from_slice::<ApiError>(&response.body)
        .is_ok_and(|error| error.code == ErrorCode::UseDpopNonce)
}

impl SyncClient {
    fn url(&self, path: &str) -> String {
        format!("{}{}", self.base_url.as_str().trim_end_matches('/'), path)
    }

    async fn send(&self, request: HttpRequest) -> SyncResult<HttpResponse> {
        self.transport
            .send(request)
            .await
            .map_err(|e| SyncError::Network { message: e.message })
    }

    /// Signs and sends one request. A `use_dpop_nonce` challenge that carries a nonce is answered
    /// once with a re-signed request; a second challenge is returned as the response. Every
    /// `DPoP-Nonce` header the server sends replaces `nonce`.
    pub(super) async fn exchange(
        &self,
        method: Method,
        path: &str,
        access_token: Option<&str>,
        body: Option<&[u8]>,
        nonce: &mut Option<String>,
    ) -> SyncResult<HttpResponse> {
        let url = self.url(path);

        let mut retried = false;
        loop {
            let proof = self
                .prover
                .proof(&ProofRequest {
                    htm: proof_method(method),
                    htu: &url,
                    access_token,
                    nonce: nonce.as_deref(),
                })
                .map_err(|e| SyncError::Client {
                    message: format!("could not sign the DPoP proof: {e}"),
                })?;

            let mut headers = vec![Header {
                name: "dpop".into(),
                value: proof,
            }];
            if let Some(token) = access_token {
                headers.push(Header {
                    name: "authorization".into(),
                    value: format!("DPoP {token}"),
                });
            }
            if body.is_some() {
                headers.push(Header {
                    name: "content-type".into(),
                    value: "application/json".into(),
                });
            }

            let response = self
                .send(HttpRequest {
                    method,
                    url: url.clone(),
                    headers,
                    body: body.map(<[u8]>::to_vec).unwrap_or_default(),
                })
                .await?;

            let fresh = header_value(&response, "dpop-nonce").map(str::to_owned);
            if let Some(fresh) = &fresh {
                *nonce = Some(fresh.clone());
            }

            if !retried && fresh.is_some() && is_nonce_challenge(&response) {
                retried = true;
                continue;
            }
            return Ok(response);
        }
    }

    pub(super) async fn call<E: Endpoint>(
        &self,
        endpoint: &E,
        body: &E::Request,
    ) -> SyncResult<E::Response> {
        let body = encode_body(body)?;
        let path = endpoint.path();

        let response = match E::AUTH {
            Auth::None => {
                let mut headers = Vec::new();
                if body.is_some() {
                    headers.push(Header {
                        name: "content-type".into(),
                        value: "application/json".into(),
                    });
                }
                self.send(HttpRequest {
                    method: E::METHOD,
                    url: self.url(&path),
                    headers,
                    body: body.unwrap_or_default(),
                })
                .await?
            }
            Auth::Proof => {
                let mut nonce = self.auth.lock().await.nonce.clone();
                let response = self
                    .exchange(E::METHOD, &path, None, body.as_deref(), &mut nonce)
                    .await;
                self.auth.lock().await.nonce = nonce;
                response?
            }
            // Replaced by the access token path in a later task; never send this bare.
            Auth::AccessToken => {
                return Err(SyncError::Client {
                    message: "authenticated calls are not supported yet".into(),
                });
            }
        };

        if (200..300).contains(&response.status) {
            decode_body(&response.body)
        } else {
            Err(from_response(response.status, &response.body))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use keygo_api::meta::ServerInfo;

    #[test]
    fn empty_body_decodes_as_unit() {
        assert_eq!(decode_body::<()>(b""), Ok(()));
    }

    #[test]
    fn empty_body_is_not_a_struct() {
        assert!(matches!(
            decode_body::<ServerInfo>(b""),
            Err(SyncError::Server { code, .. }) if code == "invalid_response",
        ));
    }

    use super::super::client::SyncClient;
    use super::super::signer::{DpopKey, SignError};
    use super::super::testing::{FakeDpopKey, FakeTokenStore, FakeTransport};
    use base64::Engine;
    use base64::engine::general_purpose::URL_SAFE_NO_PAD;
    use keygo_api::auth::token::TokenRequest;
    use keygo_api::endpoints::Token;
    use serde_json::{Value, json};
    use std::sync::Arc;
    use url::Url;

    const SESSION: &str = r#"{"device_id":"00000000-0000-0000-0000-000000000001","access_token":"a.b.c","access_expires_in":300,"refresh_token":"kgr___________________________________________8","refresh_expires_in":86400}"#;
    const CHALLENGE: &[u8] = br#"{"code":"use_dpop_nonce","message":"m"}"#;

    fn client_for(base: &str, key: Arc<dyn DpopKey>) -> (SyncClient, Arc<FakeTransport>) {
        let transport = FakeTransport::new();
        let client = SyncClient::new(
            Url::parse(base).unwrap(),
            transport.clone(),
            key,
            Arc::new(FakeTokenStore::new()),
        )
        .unwrap();
        (client, transport)
    }

    fn client() -> (SyncClient, Arc<FakeTransport>) {
        client_for("https://kg.example", Arc::new(FakeDpopKey::new()))
    }

    fn token_request() -> TokenRequest {
        serde_json::from_value(json!({
            "refresh_token": "kgr___________________________________________8",
        }))
        .unwrap()
    }

    fn proof_claims(request: &HttpRequest) -> Value {
        let proof = header(request, "dpop").expect("request carries a dpop header");
        let claims = proof.split('.').nth(1).unwrap();
        serde_json::from_slice(&URL_SAFE_NO_PAD.decode(claims).unwrap()).unwrap()
    }

    fn header<'a>(request: &'a HttpRequest, name: &str) -> Option<&'a str> {
        request
            .headers
            .iter()
            .find(|h| h.name == name)
            .map(|h| h.value.as_str())
    }

    #[tokio::test]
    async fn proof_endpoint_signs_without_ath() {
        let (client, transport) = client();
        transport.push(200, &[], SESSION.as_bytes());

        client.call(&Token, &token_request()).await.ok().unwrap();

        let request = &transport.requests()[0];
        let claims = proof_claims(request);
        assert_eq!(claims["htm"], "POST");
        assert_eq!(claims["htu"], "https://kg.example/api/v1/auth/token");
        assert!(claims.get("ath").is_none());
        assert_eq!(header(request, "authorization"), None);
    }

    #[tokio::test]
    async fn nonce_challenge_retries_with_the_new_nonce() {
        let (client, transport) = client();
        transport.push(400, &[("DPoP-Nonce", "n1")], CHALLENGE);
        transport.push(200, &[], SESSION.as_bytes());

        client.call(&Token, &token_request()).await.ok().unwrap();

        let requests = transport.requests();
        assert_eq!(requests.len(), 2);
        let (first, second) = (proof_claims(&requests[0]), proof_claims(&requests[1]));
        assert!(first.get("nonce").is_none());
        assert_eq!(second["nonce"], "n1");
        assert_ne!(first["jti"], second["jti"]);
    }

    #[tokio::test]
    async fn second_nonce_challenge_is_returned() {
        let (client, transport) = client();
        transport.push(400, &[("DPoP-Nonce", "n1")], CHALLENGE);
        transport.push(400, &[("DPoP-Nonce", "n2")], CHALLENGE);

        let error = client.call(&Token, &token_request()).await.err().unwrap();

        assert_eq!(transport.requests().len(), 2);
        assert!(matches!(error, SyncError::Server { code, .. } if code == "use_dpop_nonce"));
    }

    #[tokio::test]
    async fn challenge_without_header_is_returned() {
        let (client, transport) = client();
        transport.push(400, &[], CHALLENGE);

        let error = client.call(&Token, &token_request()).await.err().unwrap();

        assert_eq!(transport.requests().len(), 1);
        assert!(matches!(error, SyncError::Server { code, .. } if code == "use_dpop_nonce"));
    }

    #[tokio::test]
    async fn last_nonce_is_reused() {
        let (client, transport) = client();
        transport.push(400, &[("DPoP-Nonce", "n1")], CHALLENGE);
        transport.push(200, &[], SESSION.as_bytes());
        transport.push(200, &[], SESSION.as_bytes());

        client.call(&Token, &token_request()).await.ok().unwrap();
        client.call(&Token, &token_request()).await.ok().unwrap();

        let requests = transport.requests();
        assert_eq!(requests.len(), 3);
        assert_eq!(proof_claims(&requests[2])["nonce"], "n1");
    }

    #[tokio::test]
    async fn nonce_on_a_success_is_kept() {
        let (client, transport) = client();
        transport.push(200, &[("DPoP-Nonce", "n1")], SESSION.as_bytes());
        transport.push(200, &[], SESSION.as_bytes());

        client.call(&Token, &token_request()).await.ok().unwrap();
        client.call(&Token, &token_request()).await.ok().unwrap();

        let requests = transport.requests();
        assert_eq!(requests.len(), 2);
        assert_eq!(proof_claims(&requests[1])["nonce"], "n1");
    }

    #[tokio::test]
    async fn trailing_slash_in_base_url_is_harmless() {
        let (plain, plain_transport) = client();
        let (slashed, slashed_transport) =
            client_for("https://kg.example/", Arc::new(FakeDpopKey::new()));
        plain_transport.push(200, &[], SESSION.as_bytes());
        slashed_transport.push(200, &[], SESSION.as_bytes());

        plain.call(&Token, &token_request()).await.ok().unwrap();
        slashed.call(&Token, &token_request()).await.ok().unwrap();

        let (a, b) = (
            &plain_transport.requests()[0],
            &slashed_transport.requests()[0],
        );
        assert_eq!(a.url, b.url);
        assert_eq!(proof_claims(a)["htu"], proof_claims(b)["htu"]);
    }

    struct FailingKey(FakeDpopKey);

    impl DpopKey for FailingKey {
        fn public_key(&self) -> Vec<u8> {
            self.0.public_key()
        }

        fn sign(&self, _: Vec<u8>) -> Result<Vec<u8>, SignError> {
            Err(SignError {
                message: "keystore locked".into(),
            })
        }
    }

    #[tokio::test]
    async fn keystore_failure_is_a_client_error() {
        let (client, transport) = client_for(
            "https://kg.example",
            Arc::new(FailingKey(FakeDpopKey::new())),
        );

        let error = client.call(&Token, &token_request()).await.err().unwrap();

        assert!(matches!(error, SyncError::Client { .. }));
        assert!(transport.requests().is_empty());
    }
}
