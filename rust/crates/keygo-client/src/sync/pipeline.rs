use super::client::SyncClient;
use super::error::{SyncError, SyncResult, from_response};
use super::state::AuthState;
use super::tokens::{StoredSession, TokenStoreError};
use super::transport::{Header, HttpRequest, HttpResponse};
use keygo_api::auth::Session;
use keygo_api::auth::token::TokenRequest;
use keygo_api::endpoints::Token;
use keygo_api::{ApiError, Auth, Endpoint, ErrorCode, Method};
use keygo_dpop::{Htm, ProofRequest};
use serde::Serialize;
use serde::de::DeserializeOwned;
use std::ops::DerefMut;
use std::sync::{MutexGuard, PoisonError};
use zeroize::Zeroizing;

fn to_json<T: Serialize>(value: &T) -> SyncResult<Vec<u8>> {
    serde_json::to_vec(value).map_err(|e| SyncError::Client {
        message: format!("request does not serialize: {e}"),
    })
}

/// A serialized `null` (the `()` body) is sent as no body at all.
fn encode_body<T: Serialize>(value: &T) -> SyncResult<Option<Vec<u8>>> {
    let json = to_json(value)?;
    Ok((json != b"null").then_some(json))
}

fn json_content_type() -> Header {
    Header {
        name: "content-type".into(),
        value: "application/json".into(),
    }
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

impl HttpResponse {
    fn header_value(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|h| h.name.eq_ignore_ascii_case(name))
            .map(|h| h.value.as_str())
    }

    fn is_success(&self) -> bool {
        (200..300).contains(&self.status)
    }

    /// A 2xx is never an error, whatever its body says.
    fn api_error(&self) -> Option<ApiError> {
        if self.is_success() {
            return None;
        }
        serde_json::from_slice(&self.body).ok()
    }

    fn is_invalid_token(&self) -> bool {
        self.api_error()
            .is_some_and(|error| error.code == ErrorCode::InvalidToken)
    }

    /// An unknown revoke reason becomes `None`, so a newer server can still sign an older app out.
    fn sign_out_error(&self) -> Option<SyncError> {
        let error = self.api_error()?;
        matches!(
            error.code,
            ErrorCode::SessionRevoked | ErrorCode::RefreshExpired
        )
        .then(|| SyncError::SignedOut {
            reason: error.reason.and_then(|reason| reason.parse().ok()),
        })
    }
}

pub(super) fn store_failure(error: TokenStoreError) -> SyncError {
    SyncError::Client {
        message: format!("token store failed: {error}"),
    }
}

impl SyncClient {
    fn url(&self, path: &str) -> SyncResult<String> {
        Ok(self.base_url.join(path)?.to_string())
    }

    fn nonce_slot(&self) -> MutexGuard<'_, Option<String>> {
        self.nonce.lock().unwrap_or_else(PoisonError::into_inner)
    }

    fn remember_nonce_and_check_challenge(&self, response: &HttpResponse) -> bool {
        let Some(fresh) = response.header_value("dpop-nonce") else {
            return false;
        };
        *self.nonce_slot() = Some(fresh.to_owned());
        response
            .api_error()
            .is_some_and(|error| error.code == ErrorCode::UseDpopNonce)
    }

    async fn send(&self, request: HttpRequest) -> SyncResult<HttpResponse> {
        self.transport
            .send(request)
            .await
            .map_err(|e| SyncError::Network { message: e.message })
    }

    async fn send_signed_once(
        &self,
        method: Method,
        url: &str,
        access_token: Option<&str>,
        body: Option<&[u8]>,
    ) -> SyncResult<HttpResponse> {
        let nonce = self.nonce_slot().clone();
        let proof = self
            .prover
            .proof(&ProofRequest {
                htm: proof_method(method),
                htu: url,
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
            headers.push(json_content_type());
        }

        self.send(HttpRequest {
            method,
            url: url.to_owned(),
            headers,
            body: body.map(<[u8]>::to_vec).unwrap_or_default(),
        })
        .await
    }

    /// Signs and sends one request, and re-signs once if the server answers with a nonce challenge
    /// that carries a nonce.
    /// Every `DPoP-Nonce` header the server sends replaces the stored nonce.
    pub(super) async fn send_singed(
        &self,
        method: Method,
        path: &str,
        access_token: Option<&str>,
        body: Option<&[u8]>,
    ) -> SyncResult<HttpResponse> {
        let url = self.url(path)?;

        let response = self
            .send_signed_once(method, &url, access_token, body)
            .await?;
        if self.remember_nonce_and_check_challenge(&response) {
            let retried = self
                .send_signed_once(method, &url, access_token, body)
                .await?;
            self.remember_nonce_and_check_challenge(&retried);
            return Ok(retried);
        }
        Ok(response)
    }

    pub(super) async fn end_session(&self, state: &mut AuthState) -> Result<(), TokenStoreError> {
        state.reset();
        *self.nonce_slot() = None;
        self.tokens.clear().await
    }

    /// If the store refuses, `state` keeps the live copy and the next call tries again.
    async fn flush_unsaved(&self, state: &mut AuthState) {
        let Some(unsaved) = &state.unsaved else {
            return;
        };
        if self.tokens.save(unsaved).await.is_ok() {
            state.unsaved = None;
        }
    }

    async fn refresh(&self, state: &mut AuthState) -> SyncResult<Zeroizing<String>> {
        // If the previous refresh could not successfully store the refresh token, it is
        // kept in-memory, so we will use that one instead.
        let refresh_token = match &state.unsaved {
            Some(unsaved) => &unsaved.refresh_token,
            None => {
                &self
                    .tokens
                    .load()
                    .await
                    .map_err(store_failure)?
                    .ok_or(SyncError::SignedOut { reason: None })?
                    .refresh_token
            }
        };

        let body = Zeroizing::new(to_json(&TokenRequest { refresh_token })?);

        let sent_at = self.clock.now_secs();
        let response = self
            .send_singed(Token::METHOD, &Token.path(), None, Some(body.as_slice()))
            .await?;

        if !response.is_success() {
            return Err(self.failure(state, &response).await);
        }

        let body = Zeroizing::new(response.body);
        let session: Session = decode_body(&body)?;

        let token = Zeroizing::new(session.access.access_token.expose().to_owned());
        self.adopt_session(state, session, sent_at).await;
        Ok(token)
    }

    pub(super) async fn adopt_session(
        &self,
        state: &mut AuthState,
        session: Session,
        sent_at: u64,
    ) {
        state.unsaved = Some(StoredSession {
            device_id: session.device_id,
            refresh_token: session.refresh_token,
            refresh_expires_at: sent_at.saturating_add(session.refresh_expires_in),
        });
        self.flush_unsaved(state).await;
        state.replace_access(session.access, sent_at);
    }

    /// Only a refresh holds the lock across the network. It is released before the request itself.
    async fn access_token(&self, rejected: Option<u64>) -> SyncResult<(Zeroizing<String>, u64)> {
        let mut state = self.auth.lock().await;
        self.flush_unsaved(&mut state).await;

        let now = self.clock.now_secs();
        let usable = Some(state.generation) != rejected;
        let live = state
            .access()
            .filter(|access| usable && now < access.expires_at);
        let token = match live {
            Some(access) => Zeroizing::new(access.token.expose().to_owned()),
            None => self.refresh(&mut state).await?,
        };
        Ok((token, state.generation))
    }

    /// An `invalid_token` earns one retry. The refresh before it is skipped if another caller has
    /// already replaced the token.
    async fn authenticated(
        &self,
        method: Method,
        path: &str,
        body: Option<&[u8]>,
    ) -> SyncResult<HttpResponse> {
        let mut rejected = None;
        loop {
            let (token, generation) = self.access_token(rejected).await?;

            let response = self
                .send_singed(method, path, Some(token.as_str()), body)
                .await?;

            if rejected.is_some() || !response.is_invalid_token() {
                return Ok(response);
            }
            rejected = Some(generation);
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
                self.send(HttpRequest {
                    method: E::METHOD,
                    url: self.url(&path)?,
                    headers: body.is_some().then(json_content_type).into_iter().collect(),
                    body: body.unwrap_or_default(),
                })
                .await?
            }
            Auth::Proof => {
                self.send_singed(E::METHOD, &path, None, body.as_deref())
                    .await?
            }
            Auth::AccessToken => {
                self.authenticated(E::METHOD, &path, body.as_deref())
                    .await?
            }
        };

        if response.is_success() {
            return decode_body(&response.body);
        }
        Err(self
            .failure(self.auth.lock().await.deref_mut(), &response)
            .await)
    }

    async fn failure(&self, state: &mut AuthState, response: &HttpResponse) -> SyncError {
        let Some(signed_out) = response.sign_out_error() else {
            return from_response(response.status, &response.body);
        };
        // The server has killed this token, so a failed clear is harmless.
        let _ = self.end_session(state).await;
        signed_out
    }
}

#[cfg(test)]
mod tests {
    use super::super::signer::SignError;
    use super::super::testing::{
        FakeSigner, FakeTokenStore, FakeTransport, FixedClock, client_for, error_body, fake_client,
        header,
    };
    use super::super::tokens::TokenStore;
    use super::*;
    use base64::Engine;
    use base64::engine::general_purpose::URL_SAFE_NO_PAD;
    use keygo_api::domain::RevokeReason;
    use keygo_api::meta::ServerInfo;
    use keygo_core::DeviceId;
    use keygo_dpop::{DpopSigner, Signature, VerifyingKey};
    use serde_json::{Value, json};
    use std::sync::Arc;
    use std::sync::atomic::{AtomicBool, Ordering};
    use url::Url;

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

    const SESSION: &str = r#"{"device_id":"00000000-0000-0000-0000-000000000001","access_token":"a.b.c","access_expires_in":300,"refresh_token":"kgr___________________________________________8","refresh_expires_in":86400}"#;
    const CHALLENGE: &[u8] = br#"{"code":"use_dpop_nonce","message":"m"}"#;

    fn token_request() -> TokenRequest {
        serde_json::from_value(json!({"refresh_token": OLD_REFRESH})).unwrap()
    }

    fn proof_claims(request: &HttpRequest) -> Value {
        let proof = header(request, "dpop").expect("request carries a dpop header");
        let claims = proof.split('.').nth(1).unwrap();
        serde_json::from_slice(&URL_SAFE_NO_PAD.decode(claims).unwrap()).unwrap()
    }

    #[tokio::test]
    async fn proof_endpoint_signs_without_ath() {
        let (client, transport) = fake_client();
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
        let (client, transport) = fake_client();
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
        let (client, transport) = fake_client();
        transport.push(400, &[("DPoP-Nonce", "n1")], CHALLENGE);
        transport.push(400, &[("DPoP-Nonce", "n2")], CHALLENGE);

        let error = client.call(&Token, &token_request()).await.err().unwrap();

        assert_eq!(transport.requests().len(), 2);
        assert!(matches!(error, SyncError::Server { code, .. } if code == "use_dpop_nonce"));
    }

    #[tokio::test]
    async fn challenge_without_header_is_returned() {
        let (client, transport) = fake_client();
        transport.push(400, &[], CHALLENGE);

        let error = client.call(&Token, &token_request()).await.err().unwrap();

        assert_eq!(transport.requests().len(), 1);
        assert!(matches!(error, SyncError::Server { code, .. } if code == "use_dpop_nonce"));
    }

    #[tokio::test]
    async fn last_nonce_is_reused() {
        let (client, transport) = fake_client();
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
        let (client, transport) = fake_client();
        transport.push(200, &[("DPoP-Nonce", "n1")], SESSION.as_bytes());
        transport.push(200, &[], SESSION.as_bytes());

        client.call(&Token, &token_request()).await.ok().unwrap();
        client.call(&Token, &token_request()).await.ok().unwrap();

        let requests = transport.requests();
        assert_eq!(requests.len(), 2);
        assert_eq!(proof_claims(&requests[1])["nonce"], "n1");
    }

    #[tokio::test]
    async fn a_response_without_a_nonce_keeps_a_newer_one() {
        let (client, transport) = fake_client();
        transport.push(200, &[("DPoP-Nonce", "n1")], SESSION.as_bytes());
        transport.push(200, &[], SESSION.as_bytes());
        transport.push(200, &[], SESSION.as_bytes());

        let request = token_request();
        let (first, second) =
            tokio::join!(client.call(&Token, &request), client.call(&Token, &request),);
        first.ok().unwrap();
        second.ok().unwrap();
        client.call(&Token, &request).await.ok().unwrap();

        assert_eq!(proof_claims(&transport.requests()[2])["nonce"], "n1");
    }

    #[tokio::test]
    async fn ending_the_session_forgets_the_nonce() {
        let h = fresh().await;
        h.transport.push(204, &[("DPoP-Nonce", "n1")], b"");
        h.client.logout().await.unwrap();

        h.transport.push(200, &[], SESSION.as_bytes());
        h.client.call(&Token, &token_request()).await.ok().unwrap();

        let requests = h.transport.requests();
        assert!(
            proof_claims(requests.last().unwrap())
                .get("nonce")
                .is_none()
        );
    }

    #[tokio::test]
    async fn trailing_slash_in_base_url_is_harmless() {
        let (plain, plain_transport) = fake_client();
        let (slashed, slashed_transport) =
            client_for("https://kg.example/", Arc::new(FakeSigner::new()));
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

    struct FailingSigner(FakeSigner);

    impl DpopSigner for FailingSigner {
        type Error = SignError;

        fn verifying_key(&self) -> &VerifyingKey {
            self.0.verifying_key()
        }

        fn sign(&self, _: &[u8]) -> Result<Signature, SignError> {
            Err(SignError {
                message: "keystore locked".into(),
            })
        }
    }

    #[tokio::test]
    async fn keystore_failure_is_a_client_error() {
        let (client, transport) = client_for(
            "https://kg.example",
            Arc::new(FailingSigner(FakeSigner::new())),
        );

        let error = client.call(&Token, &token_request()).await.err().unwrap();

        assert!(matches!(
            error,
            SyncError::Client { message } if message.contains("keystore locked"),
        ));
        assert!(transport.requests().is_empty());
    }

    const OLD_REFRESH: &str = "kgr___________________________________________8";
    const ROTATED_REFRESH: &str = "kgr_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    const NEXT_REFRESH: &str = "kgr_AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";
    const DEVICES: &[u8] = br#"{"devices":[]}"#;
    /// Requests `fresh` has already spent.
    const FRESH: usize = 2;

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
            self.spent_tokens().len()
        }

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
            assert_eq!(self.store.current_refresh_token(), None);
        }
    }

    fn unseeded() -> Harness {
        let transport = FakeTransport::new();
        let store = Arc::new(FakeTokenStore::new());
        let clock = FixedClock::new(1_000);
        let client = SyncClient::new(
            Url::parse("https://kg.example").unwrap(),
            transport.clone(),
            Arc::new(FakeSigner::new()),
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

    async fn after_first_call(harness: Harness) -> Harness {
        harness.transport.push(200, &[], &session("a.b.c"));
        harness.transport.push(200, &[], DEVICES);
        harness.client.devices().await.unwrap();
        harness
    }

    /// Holds a live access token: one refresh and one `devices()` have run.
    async fn fresh() -> Harness {
        after_first_call(seeded()).await
    }

    /// Holds a rotated token the store refused, with saves still failing.
    async fn refused() -> Harness {
        let harness = seeded();
        harness.store.fail_saves();
        after_first_call(harness).await
    }

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
        assert_eq!(
            h.store.current_refresh_token().as_deref(),
            Some(ROTATED_REFRESH)
        );

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
        assert_eq!(
            h.store.current_refresh_token().as_deref(),
            Some(OLD_REFRESH)
        );
    }

    #[tokio::test]
    async fn failed_save_is_retried_on_the_next_call() {
        let h = refused().await;

        h.store.allow_saves();
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();

        assert_eq!(h.token_requests(), 1);
        assert_eq!(
            h.store.current_refresh_token().as_deref(),
            Some(ROTATED_REFRESH)
        );
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
            Arc::new(FakeSigner::new()),
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
        assert_eq!(h.store.current_refresh_token(), None);
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
        assert_eq!(h.store.current_refresh_token(), None);
    }

    #[tokio::test]
    async fn unknown_revoke_reason_still_signs_out() {
        let h = seeded();
        h.transport.push(401, &[], &revoked_body("brand_new"));

        assert_eq!(
            h.client.devices().await.unwrap_err(),
            SyncError::SignedOut { reason: None },
        );
        assert_eq!(h.store.current_refresh_token(), None);
    }

    #[tokio::test]
    async fn a_success_status_never_signs_out() {
        let h = fresh().await;
        h.transport.push(200, &[], &error_body("session_revoked"));

        let error = h.client.devices().await.unwrap_err();

        assert!(matches!(error, SyncError::Server { code, .. } if code == "invalid_response"));
        assert_eq!(
            h.store.current_refresh_token().as_deref(),
            Some(ROTATED_REFRESH)
        );
    }

    #[tokio::test]
    async fn network_failure_during_refresh_keeps_store() {
        let h = seeded();
        h.transport.push_network_error("offline");

        let error = h.client.devices().await.unwrap_err();

        assert!(matches!(error, SyncError::Network { .. }));
        assert_eq!(
            h.store.current_refresh_token().as_deref(),
            Some(OLD_REFRESH)
        );
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
        // The server still accepts this token, so this cannot pass as an offline logout.
        assert_eq!(
            h.store.current_refresh_token().as_deref(),
            Some(ROTATED_REFRESH)
        );
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
        assert_eq!(
            h.store.current_refresh_token().as_deref(),
            Some(OLD_REFRESH)
        );

        h.transport.push(200, &[], &session("a.b.c"));
        h.transport.push(200, &[], DEVICES);
        h.client.devices().await.unwrap();

        assert_eq!(h.transport.requests().len(), 2);
    }
}
