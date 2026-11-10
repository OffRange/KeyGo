use super::client::SyncClient;
use super::error::{SyncError, SyncResult, from_response};
use super::state::{AccessState, AuthState};
use super::tokens::{StoredSession, TokenStoreError};
use super::transport::{Header, HttpRequest, HttpResponse};
use keygo_api::auth::Session;
use keygo_api::auth::token::TokenRequest;
use keygo_api::endpoints::Token;
use keygo_api::{ApiError, Auth, Endpoint, ErrorCode, Method};
use keygo_dpop::{Htm, ProofRequest};
use serde::Serialize;
use serde::de::DeserializeOwned;
use zeroize::Zeroizing;

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

fn is_success(response: &HttpResponse) -> bool {
    (200..300).contains(&response.status)
}

/// The error body of a failed response. A 2xx is never an error, whatever its body says.
fn api_error(response: &HttpResponse) -> Option<ApiError> {
    if is_success(response) {
        return None;
    }
    serde_json::from_slice(&response.body).ok()
}

fn is_invalid_token(response: &HttpResponse) -> bool {
    api_error(response).is_some_and(|error| error.code == ErrorCode::InvalidToken)
}

/// The `SignedOut` a response means, if the server says the session is over. A reason this build
/// does not know is `None`, so a newer server still signs an older app out.
fn sign_out_error(response: &HttpResponse) -> Option<SyncError> {
    let error = api_error(response)?;
    matches!(
        error.code,
        ErrorCode::SessionRevoked | ErrorCode::RefreshExpired
    )
    .then(|| SyncError::SignedOut {
        reason: error.reason.and_then(|reason| reason.parse().ok()),
    })
}

pub(super) fn store_failure(error: TokenStoreError) -> SyncError {
    SyncError::Client {
        message: format!("token store failed: {error}"),
    }
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

    /// Forgets the session, in memory and in the store. The caller holds the `AuthState` lock, so
    /// a refresh that is still running cannot save a token after this.
    ///
    /// `AuthState` is reset even when the store fails. What that failure means is the caller's to
    /// decide: an entry the server has already killed is only refused and cleared again, but one
    /// it still accepts would bring the session back on the next call.
    pub(super) async fn end_session(&self, state: &mut AuthState) -> Result<(), TokenStoreError> {
        state.reset();
        self.tokens.clear().await
    }

    /// Writes the rotated session held in `state`, if any. A refusal is not the caller's failure:
    /// `state` keeps the live copy and the next call writes it again.
    async fn flush_unsaved(&self, state: &mut AuthState) {
        let Some(unsaved) = &state.unsaved else {
            return;
        };
        if self.tokens.save(unsaved).await.is_ok() {
            state.unsaved = None;
        }
    }

    /// Spends the live refresh token on a new session and returns the new access token. `state`
    /// is the locked `AuthState`, held for the whole refresh so concurrent callers share its
    /// result. It goes straight to `exchange`, because `call` would take the lock again.
    ///
    /// The rotated session goes into `state` before it is written, so neither a refused save nor
    /// a caller dropped during the write can lose the only copy of the live token.
    async fn refresh_locked(&self, state: &mut AuthState) -> SyncResult<Zeroizing<String>> {
        let refresh_token = match &state.unsaved {
            Some(unsaved) => unsaved.duplicate().refresh_token,
            None => {
                self.tokens
                    .load()
                    .await
                    .map_err(store_failure)?
                    .ok_or(SyncError::SignedOut { reason: None })?
                    .refresh_token
            }
        };

        let request = TokenRequest { refresh_token };
        // Not `encode_body`: this body is a secret and should not linger in a `Value`.
        let body = Zeroizing::new(serde_json::to_vec(&request).map_err(|e| SyncError::Client {
            message: format!("request does not serialize: {e}"),
        })?);

        // Read before sending, so a token never looks like it lives longer than it does.
        let now = self.clock.now_secs();
        let response = self
            .exchange(
                Token::METHOD,
                &Token.path(),
                None,
                Some(body.as_slice()),
                &mut state.nonce,
            )
            .await?;

        if !is_success(&response) {
            if let Some(signed_out) = sign_out_error(&response) {
                // The server has killed this token, so an entry left behind is only refused again.
                let _ = self.end_session(state).await;
                return Err(signed_out);
            }
            return Err(from_response(response.status, &response.body));
        }

        let body = Zeroizing::new(response.body);
        let session: Session = decode_body(&body)?;

        state.unsaved = Some(StoredSession {
            device_id: session.device_id,
            refresh_token: session.refresh_token,
            refresh_expires_at: now.saturating_add(session.refresh_expires_in),
        });
        self.flush_unsaved(state).await;

        let token = Zeroizing::new(session.access.access_token.expose().to_owned());
        state.replace_access(AccessState {
            token: session.access.access_token,
            expires_at: now.saturating_add(session.access.access_expires_in),
        });
        Ok(token)
    }

    /// A live access token, refreshed first if there is none, with the generation it belongs to
    /// and the nonce to send. The lock is released on return: only a refresh holds it across the
    /// network, never the request itself.
    async fn access_token(&self) -> SyncResult<(Zeroizing<String>, u64, Option<String>)> {
        let mut state = self.auth.lock().await;
        self.flush_unsaved(&mut state).await;
        let now = self.clock.now_secs();
        let live = state.access().filter(|access| now < access.expires_at);
        let token = match live {
            Some(access) => Zeroizing::new(access.token.expose().to_owned()),
            None => self.refresh_locked(&mut state).await?,
        };
        Ok((token, state.generation, state.nonce.clone()))
    }

    /// Sends one request with the access token. An `invalid_token` earns one refresh and one
    /// retry; the refresh is skipped when another caller has already replaced the rejected token.
    async fn authenticated(
        &self,
        method: Method,
        path: &str,
        body: Option<&[u8]>,
    ) -> SyncResult<HttpResponse> {
        let mut retried = false;
        loop {
            let (token, generation, mut nonce) = self.access_token().await?;

            let response = self
                .exchange(method, path, Some(token.as_str()), body, &mut nonce)
                .await;
            self.auth.lock().await.nonce = nonce;
            let response = response?;

            if retried || !is_invalid_token(&response) {
                return Ok(response);
            }
            retried = true;

            let mut state = self.auth.lock().await;
            if state.generation == generation {
                self.refresh_locked(&mut state).await?;
            }
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
            Auth::AccessToken => {
                self.authenticated(E::METHOD, &path, body.as_deref())
                    .await?
            }
        };

        if is_success(&response) {
            return decode_body(&response.body);
        }
        if let Some(signed_out) = sign_out_error(&response) {
            let mut state = self.auth.lock().await;
            // The server has killed this token, so an entry left behind is only refused again.
            let _ = self.end_session(&mut state).await;
            return Err(signed_out);
        }
        Err(from_response(response.status, &response.body))
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
    use super::super::testing::{FakeDpopKey, FakeTokenStore, FakeTransport, FixedClock};
    use super::super::tokens::TokenStore;
    use base64::Engine;
    use base64::engine::general_purpose::URL_SAFE_NO_PAD;
    use keygo_api::domain::RevokeReason;
    use keygo_core::DeviceId;
    use serde_json::{Value, json};
    use std::sync::Arc;
    use std::sync::atomic::{AtomicBool, Ordering};
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
        fn public_key(&self) -> Result<Vec<u8>, SignError> {
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

    const OLD_REFRESH: &str = "kgr___________________________________________8";
    const ROTATED_REFRESH: &str = "kgr_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    const NEXT_REFRESH: &str = "kgr_AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";
    const DEVICES: &[u8] = br#"{"devices":[]}"#;
    /// Requests `fresh` has already spent.
    const FRESH: usize = 2;

    /// A `200` from `/auth/token`.
    fn session(access_token: &str) -> Vec<u8> {
        session_with(access_token, ROTATED_REFRESH)
    }

    fn session_with(access_token: &str, refresh_token: &str) -> Vec<u8> {
        serde_json::to_vec(&json!({
            "device_id": "00000000-0000-0000-0000-000000000001",
            "access_token": access_token,
            "access_expires_in": 600,
            "refresh_token": refresh_token,
            "refresh_expires_in": 86400,
        }))
        .unwrap()
    }

    fn error_body(code: &str) -> Vec<u8> {
        serde_json::to_vec(&json!({"code": code, "message": "m"})).unwrap()
    }

    fn revoked_body(reason: &str) -> Vec<u8> {
        serde_json::to_vec(&json!({
            "code": "session_revoked",
            "message": "m",
            "reason": reason,
        }))
        .unwrap()
    }

    struct Harness {
        client: SyncClient,
        transport: Arc<FakeTransport>,
        store: Arc<FakeTokenStore>,
        clock: Arc<FixedClock>,
    }

    impl Harness {
        fn token_requests(&self) -> usize {
            self.transport
                .requests()
                .iter()
                .filter(|r| r.url.ends_with("/api/v1/auth/token"))
                .count()
        }

        fn stored_refresh_token(&self) -> Option<String> {
            self.store.current_refresh_token()
        }

        /// Every refresh token sent so far, in order.
        fn spent_tokens(&self) -> Vec<String> {
            self.transport
                .requests()
                .iter()
                .filter(|r| r.url.ends_with("/api/v1/auth/token"))
                .map(|r| {
                    let body: Value = serde_json::from_slice(&r.body).unwrap();
                    body["refresh_token"].as_str().unwrap().to_owned()
                })
                .collect()
        }

        async fn assert_signed_out_locally(&self) {
            let sent = self.transport.requests().len();

            assert_eq!(
                self.client.devices().await.unwrap_err(),
                SyncError::SignedOut { reason: None },
            );
            assert_eq!(self.transport.requests().len(), sent);
            assert_eq!(self.stored_refresh_token(), None);
        }
    }

    fn unseeded() -> Harness {
        let transport = FakeTransport::new();
        let store = Arc::new(FakeTokenStore::new());
        let clock = FixedClock::new(1_000);
        let client = SyncClient::new(
            Url::parse("https://kg.example").unwrap(),
            transport.clone(),
            Arc::new(FakeDpopKey::new()),
            store.clone(),
        )
        .unwrap()
        .with_clock(clock.clone());
        Harness {
            client,
            transport,
            store,
            clock,
        }
    }

    fn seeded() -> Harness {
        let harness = unseeded();
        harness.store.seed(StoredSession {
            device_id: DeviceId::random(),
            refresh_token: OLD_REFRESH.parse().unwrap(),
            refresh_expires_at: 1_000_000,
        });
        harness
    }

    /// Holds a live access token: one refresh and one `devices()` have run.
    async fn fresh() -> Harness {
        let harness = seeded();
        harness.transport.push(200, &[], &session("a.b.c"));
        harness.transport.push(200, &[], DEVICES);
        harness.client.devices().await.unwrap();
        harness
    }

    /// Holds a rotated token the store refused, with saves still failing.
    async fn refused() -> Harness {
        let harness = seeded();
        harness.store.fail_saves();
        harness.transport.push(200, &[], &session("a.b.c"));
        harness.transport.push(200, &[], DEVICES);
        harness.client.devices().await.unwrap();
        harness
    }

    /// Saves any session it is given, except that while `hang` is set `save` never returns.
    struct HangingStore {
        inner: FakeTokenStore,
        hang: AtomicBool,
    }

    #[async_trait::async_trait]
    impl TokenStore for HangingStore {
        async fn load(&self) -> Result<Option<StoredSession>, TokenStoreError> {
            self.inner.load().await
        }

        async fn save(&self, session: &StoredSession) -> Result<(), TokenStoreError> {
            if self.hang.load(Ordering::SeqCst) {
                std::future::pending::<()>().await;
            }
            self.inner.save(session).await
        }

        async fn clear(&self) -> Result<(), TokenStoreError> {
            self.inner.clear().await
        }
    }

    #[tokio::test]
    async fn no_stored_session_signs_out_without_sending() {
        let h = unseeded();

        assert_eq!(
            h.client.devices().await.unwrap_err(),
            SyncError::SignedOut { reason: None },
        );
        assert!(h.transport.requests().is_empty());
    }

    #[tokio::test]
    async fn access_token_sends_token_and_bound_proof() {
        let h = seeded();
        h.transport.push(200, &[], &session("a.b.c"));
        h.transport.push(200, &[], DEVICES);

        h.client.devices().await.unwrap();

        let request = &h.transport.requests()[1];
        assert_eq!(header(request, "authorization"), Some("DPoP a.b.c"));
        let claims = proof_claims(request);
        assert_eq!(claims["htm"], "GET");
        assert_eq!(claims["htu"], "https://kg.example/api/v1/devices");
        assert!(claims["ath"].is_string());
    }

    #[tokio::test]
    async fn refresh_carries_its_token_in_the_body_only() {
        let h = fresh().await;

        let request = &h.transport.requests()[0];
        assert_eq!(request.url, "https://kg.example/api/v1/auth/token");
        assert_eq!(header(request, "authorization"), None);
        assert!(proof_claims(request).get("ath").is_none());
        let body: Value = serde_json::from_slice(&request.body).unwrap();
        assert_eq!(body, json!({"refresh_token": OLD_REFRESH}));
    }

    #[tokio::test]
    async fn rotated_refresh_token_is_saved_and_spent_next() {
        let h = fresh().await;
        assert_eq!(h.stored_refresh_token().as_deref(), Some(ROTATED_REFRESH));

        h.clock.advance(601);
        h.transport.push(200, &[], &session("d.e.f"));
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();

        let requests = h.transport.requests();
        let body: Value = serde_json::from_slice(&requests[FRESH].body).unwrap();
        assert_eq!(body, json!({"refresh_token": ROTATED_REFRESH}));
        assert_eq!(
            header(&requests[FRESH + 1], "authorization"),
            Some("DPoP d.e.f"),
        );
    }

    #[tokio::test]
    async fn fresh_token_is_reused() {
        let h = seeded();
        h.transport.push(200, &[], &session("a.b.c"));
        h.transport.push(200, &[], DEVICES);
        h.transport.push(200, &[], DEVICES);

        h.client.devices().await.unwrap();
        h.clock.advance(599);
        h.client.devices().await.unwrap();

        assert_eq!(h.transport.requests().len(), 3);
        assert_eq!(h.token_requests(), 1);
    }

    #[tokio::test]
    async fn expired_token_refreshes_before_sending() {
        let h = seeded();
        for _ in 0..2 {
            h.transport.push(200, &[], &session("a.b.c"));
            h.transport.push(200, &[], DEVICES);
        }

        h.client.devices().await.unwrap();
        h.clock.advance(601);
        h.client.devices().await.unwrap();

        let urls: Vec<_> = h.transport.requests().into_iter().map(|r| r.url).collect();
        assert_eq!(
            urls,
            [
                "https://kg.example/api/v1/auth/token",
                "https://kg.example/api/v1/devices",
                "https://kg.example/api/v1/auth/token",
                "https://kg.example/api/v1/devices",
            ],
        );
    }

    #[tokio::test]
    async fn token_is_expired_at_its_expiry_second() {
        let h = fresh().await;
        h.clock.advance(600);
        h.transport.push(200, &[], &session("d.e.f"));
        h.transport.push(200, &[], DEVICES);

        h.client.devices().await.unwrap();

        assert_eq!(h.token_requests(), 2);
    }

    #[tokio::test]
    async fn concurrent_callers_refresh_once() {
        let h = seeded();
        h.transport.push(200, &[], &session("a.b.c"));
        h.transport.push(200, &[], DEVICES);
        h.transport.push(200, &[], DEVICES);

        let (first, second) = tokio::join!(h.client.devices(), h.client.devices());

        first.unwrap();
        second.unwrap();
        assert_eq!(h.token_requests(), 1);
    }

    #[tokio::test]
    async fn concurrent_rejected_callers_refresh_once() {
        let h = fresh().await;
        h.transport.push(401, &[], &error_body("invalid_token"));
        h.transport.push(401, &[], &error_body("invalid_token"));
        h.transport.push(200, &[], &session("d.e.f"));
        h.transport.push(200, &[], DEVICES);
        h.transport.push(200, &[], DEVICES);

        let (first, second) = tokio::join!(h.client.devices(), h.client.devices());

        first.unwrap();
        second.unwrap();
        // One from `fresh`, one here.
        assert_eq!(h.token_requests(), 2);
    }

    #[tokio::test]
    async fn failed_save_still_uses_the_rotated_token() {
        let h = seeded();
        h.store.fail_saves();
        h.transport.push(200, &[], &session("a.b.c"));
        h.transport.push(200, &[], DEVICES);

        h.client.devices().await.unwrap();

        let requests = h.transport.requests();
        assert_eq!(requests.len(), 2);
        assert_eq!(header(&requests[1], "authorization"), Some("DPoP a.b.c"));
        assert_eq!(h.stored_refresh_token().as_deref(), Some(OLD_REFRESH));
    }

    #[tokio::test]
    async fn failed_save_is_retried_on_the_next_call() {
        let h = refused().await;

        h.store.allow_saves();
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();

        assert_eq!(h.token_requests(), 1);
        assert_eq!(h.stored_refresh_token().as_deref(), Some(ROTATED_REFRESH));
    }

    #[tokio::test]
    async fn the_store_is_trusted_again_after_a_retry_succeeds() {
        let h = refused().await;
        h.store.allow_saves();
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();

        // Only a client that went back to the store can see this entry.
        h.store.seed(StoredSession {
            device_id: DeviceId::random(),
            refresh_token: NEXT_REFRESH.parse().unwrap(),
            refresh_expires_at: 1_000_000,
        });
        h.clock.advance(601);
        h.transport.push(200, &[], &session("d.e.f"));
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();

        assert_eq!(h.spent_tokens(), [OLD_REFRESH, NEXT_REFRESH]);
    }

    #[tokio::test]
    async fn refresh_after_a_failed_save_spends_the_rotated_token() {
        let h = refused().await;

        h.clock.advance(601);
        h.transport.push(200, &[], &session("d.e.f"));
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();

        assert_eq!(h.spent_tokens(), [OLD_REFRESH, ROTATED_REFRESH]);
        let requests = h.transport.requests();
        assert_eq!(header(&requests[3], "authorization"), Some("DPoP d.e.f"));
    }

    #[tokio::test]
    async fn a_failed_refresh_keeps_a_token_the_store_refused() {
        let h = refused().await;

        h.clock.advance(601);
        h.transport.push_network_error("offline");
        h.client.devices().await.unwrap_err();
        h.transport.push(500, &[], &error_body("internal"));
        h.client.devices().await.unwrap_err();
        h.transport
            .push(200, &[], &session_with("d.e.f", NEXT_REFRESH));
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();

        assert_eq!(
            h.spent_tokens(),
            [
                OLD_REFRESH,
                ROTATED_REFRESH,
                ROTATED_REFRESH,
                ROTATED_REFRESH,
            ],
        );
    }

    #[tokio::test]
    async fn a_second_rotation_replaces_a_token_the_store_refused() {
        let h = refused().await;

        h.clock.advance(601);
        h.transport
            .push(200, &[], &session_with("d.e.f", NEXT_REFRESH));
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();
        h.clock.advance(601);
        h.transport.push(200, &[], &session("g.h.i"));
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();

        assert_eq!(
            h.spent_tokens(),
            [OLD_REFRESH, ROTATED_REFRESH, NEXT_REFRESH],
        );
    }

    #[tokio::test]
    async fn invalid_token_after_a_failed_save_spends_the_rotated_token() {
        let h = refused().await;

        h.transport.push(401, &[], &error_body("invalid_token"));
        h.transport
            .push(200, &[], &session_with("d.e.f", NEXT_REFRESH));
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();

        assert_eq!(h.spent_tokens(), [OLD_REFRESH, ROTATED_REFRESH]);
    }

    #[tokio::test]
    async fn a_caller_dropped_during_the_write_keeps_the_rotated_token() {
        let store = Arc::new(HangingStore {
            inner: FakeTokenStore::new(),
            hang: AtomicBool::new(true),
        });
        store.inner.seed(StoredSession {
            device_id: DeviceId::random(),
            refresh_token: OLD_REFRESH.parse().unwrap(),
            refresh_expires_at: 1_000_000,
        });
        let transport = FakeTransport::new();
        let client = SyncClient::new(
            Url::parse("https://kg.example").unwrap(),
            transport.clone(),
            Arc::new(FakeDpopKey::new()),
            store.clone(),
        )
        .unwrap()
        .with_clock(FixedClock::new(1_000));

        transport.push(200, &[], &session("a.b.c"));
        tokio::select! {
            biased;
            _ = client.devices() => panic!("the save should still be hanging"),
            () = async {
                for _ in 0..16 {
                    tokio::task::yield_now().await;
                }
            } => {}
        }
        assert_eq!(transport.requests().len(), 1);

        store.hang.store(false, Ordering::SeqCst);
        transport.push(200, &[], &session_with("d.e.f", NEXT_REFRESH));
        transport.push(200, &[], DEVICES);
        client.devices().await.unwrap();

        let body: Value = serde_json::from_slice(&transport.requests()[1].body).unwrap();
        assert_eq!(body, json!({"refresh_token": ROTATED_REFRESH}));
        assert_eq!(
            store.inner.current_refresh_token().as_deref(),
            Some(NEXT_REFRESH),
        );
    }

    #[tokio::test]
    async fn concurrent_callers_share_a_token_the_store_refused() {
        let h = seeded();
        h.store.fail_saves();
        h.transport.push(200, &[], &session("a.b.c"));
        h.transport.push(200, &[], DEVICES);
        h.transport.push(200, &[], DEVICES);

        let (first, second) = tokio::join!(h.client.devices(), h.client.devices());

        first.unwrap();
        second.unwrap();
        assert_eq!(h.token_requests(), 1);
    }

    #[tokio::test]
    async fn an_ordinary_call_signing_out_forgets_a_token_the_store_refused() {
        let h = refused().await;

        h.transport.push(401, &[], &revoked_body("reuse_detected"));
        h.client.devices().await.unwrap_err();

        h.store.allow_saves();
        h.assert_signed_out_locally().await;
    }

    #[tokio::test]
    async fn a_revoked_refresh_forgets_a_token_the_store_refused() {
        let h = refused().await;

        h.clock.advance(601);
        h.transport.push(401, &[], &revoked_body("reuse_detected"));
        h.client.devices().await.unwrap_err();

        h.store.allow_saves();
        h.assert_signed_out_locally().await;
    }

    #[tokio::test]
    async fn an_expired_refresh_forgets_a_token_the_store_refused() {
        let h = refused().await;

        h.clock.advance(601);
        h.transport.push(401, &[], &error_body("refresh_expired"));
        h.client.devices().await.unwrap_err();

        h.store.allow_saves();
        h.assert_signed_out_locally().await;
    }

    #[tokio::test]
    async fn logout_forgets_a_token_the_store_refused() {
        let h = refused().await;

        h.transport.push(204, &[], b"");
        h.client.logout().await.unwrap();

        h.store.allow_saves();
        h.assert_signed_out_locally().await;
    }

    #[tokio::test]
    async fn invalid_token_refreshes_and_retries_once() {
        let h = fresh().await;
        h.transport.push(401, &[], &error_body("invalid_token"));
        h.transport.push(200, &[], &session("d.e.f"));
        h.transport.push(200, &[], DEVICES);

        h.client.devices().await.unwrap();

        let requests = h.transport.requests();
        assert_eq!(requests.len() - FRESH, 3);
        assert_eq!(
            header(&requests[FRESH + 2], "authorization"),
            Some("DPoP d.e.f"),
        );
    }

    #[tokio::test]
    async fn second_invalid_token_is_returned() {
        let h = fresh().await;
        h.transport.push(401, &[], &error_body("invalid_token"));
        h.transport.push(200, &[], &session("d.e.f"));
        h.transport.push(401, &[], &error_body("invalid_token"));

        let error = h.client.devices().await.unwrap_err();

        assert!(matches!(error, SyncError::Server { code, .. } if code == "invalid_token"));
        assert_eq!(h.transport.requests().len() - FRESH, 3);
    }

    #[tokio::test]
    async fn nonce_and_invalid_token_each_retry_once() {
        let h = fresh().await;
        h.transport.push(400, &[("DPoP-Nonce", "n1")], CHALLENGE);
        h.transport.push(401, &[], &error_body("invalid_token"));
        h.transport.push(200, &[], &session("d.e.f"));
        h.transport.push(200, &[], DEVICES);

        h.client.devices().await.unwrap();

        assert_eq!(h.transport.requests().len() - FRESH, 4);
    }

    #[tokio::test]
    async fn session_revoked_clears_store_and_keeps_reason() {
        let h = seeded();
        h.transport.push(401, &[], &revoked_body("reuse_detected"));

        assert_eq!(
            h.client.devices().await.unwrap_err(),
            SyncError::SignedOut {
                reason: Some(RevokeReason::ReuseDetected),
            },
        );
        assert_eq!(h.stored_refresh_token(), None);
    }

    #[tokio::test]
    async fn session_revoked_on_an_ordinary_call_ends_the_session() {
        let h = fresh().await;
        h.transport.push(401, &[], &revoked_body("logout"));

        assert_eq!(
            h.client.devices().await.unwrap_err(),
            SyncError::SignedOut {
                reason: Some(RevokeReason::Logout),
            },
        );
        h.assert_signed_out_locally().await;
    }

    #[tokio::test]
    async fn refresh_expired_clears_store() {
        let h = seeded();
        h.transport.push(401, &[], &error_body("refresh_expired"));

        assert_eq!(
            h.client.devices().await.unwrap_err(),
            SyncError::SignedOut { reason: None },
        );
        assert_eq!(h.stored_refresh_token(), None);
    }

    #[tokio::test]
    async fn unknown_revoke_reason_still_signs_out() {
        let h = seeded();
        h.transport.push(401, &[], &revoked_body("brand_new"));

        assert_eq!(
            h.client.devices().await.unwrap_err(),
            SyncError::SignedOut { reason: None },
        );
        assert_eq!(h.stored_refresh_token(), None);
    }

    #[tokio::test]
    async fn a_success_status_never_signs_out() {
        let h = fresh().await;
        h.transport.push(200, &[], &error_body("session_revoked"));

        let error = h.client.devices().await.unwrap_err();

        assert!(matches!(error, SyncError::Server { code, .. } if code == "invalid_response"));
        assert_eq!(h.stored_refresh_token().as_deref(), Some(ROTATED_REFRESH));
    }

    #[tokio::test]
    async fn network_failure_during_refresh_keeps_store() {
        let h = seeded();
        h.transport.push_network_error("offline");

        let error = h.client.devices().await.unwrap_err();

        assert!(matches!(error, SyncError::Network { .. }));
        assert_eq!(h.stored_refresh_token().as_deref(), Some(OLD_REFRESH));
    }

    #[tokio::test]
    async fn logout_ends_the_session() {
        let h = fresh().await;
        h.transport.push(204, &[], b"");

        h.client.logout().await.unwrap();

        let request = &h.transport.requests()[FRESH];
        assert_eq!(request.url, "https://kg.example/api/v1/auth/logout");
        assert_eq!(header(request, "authorization"), Some("DPoP a.b.c"));
        h.assert_signed_out_locally().await;
    }

    #[tokio::test]
    async fn logout_clears_local_state_when_offline() {
        let h = fresh().await;
        h.transport.push_network_error("offline");

        let error = h.client.logout().await.unwrap_err();

        assert!(matches!(error, SyncError::Network { .. }));
        h.assert_signed_out_locally().await;
    }

    #[tokio::test]
    async fn offline_logout_reports_a_failed_clear() {
        let h = fresh().await;
        h.store.fail_clears();
        h.transport.push_network_error("offline");

        let error = h.client.logout().await.unwrap_err();

        assert!(matches!(error, SyncError::Client { .. }));
        assert!(h.client.auth.lock().await.access().is_none());
        // Why this cannot pass as an offline logout: the server still accepts this token.
        assert_eq!(h.stored_refresh_token().as_deref(), Some(ROTATED_REFRESH));
    }

    #[tokio::test]
    async fn sent_logout_ignores_a_failed_clear() {
        let h = fresh().await;
        h.store.fail_clears();
        h.transport.push(204, &[], b"");

        h.client.logout().await.unwrap();

        assert!(h.client.auth.lock().await.access().is_none());
    }

    #[tokio::test]
    async fn fresh_auth_required_is_not_retried() {
        let h = fresh().await;
        let id = DeviceId::random();
        h.transport
            .push(403, &[], &error_body("fresh_auth_required"));

        assert_eq!(
            h.client.delete_device(id).await.unwrap_err(),
            SyncError::FreshAuthRequired,
        );

        let requests = h.transport.requests();
        assert_eq!(requests.len() - FRESH, 1);
        assert_eq!(requests[FRESH].method, Method::Delete);
        assert_eq!(
            requests[FRESH].url,
            format!("https://kg.example/api/v1/devices/{}", id.as_uuid()),
        );
    }

    #[tokio::test]
    async fn a_refresh_dropped_midway_leaves_nothing_behind() {
        let h = seeded();

        // The fake transport yields once before it answers, so this drops the call while it holds
        // the lock and is waiting on `/auth/token`.
        tokio::select! {
            biased;
            _ = h.client.devices() => panic!("the call should still be waiting"),
            () = std::future::ready(()) => {}
        }
        assert!(h.transport.requests().is_empty());
        assert_eq!(h.stored_refresh_token().as_deref(), Some(OLD_REFRESH));

        h.transport.push(200, &[], &session("a.b.c"));
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();

        assert_eq!(h.transport.requests().len(), 2);
    }
}
