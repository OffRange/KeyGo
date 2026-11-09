//! Fakes for the three host traits and the clock.

use super::signer::{DpopKey, SignError};
use super::state::Clock;
use super::tokens::{StoredSession, TokenStore, TokenStoreError};
use super::transport::{Header, HttpRequest, HttpResponse, Transport, TransportError};
use async_trait::async_trait;
use keygo_dpop::testing::TestSigner;
use std::collections::VecDeque;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};

/// Answers from a script and records what it was sent.
pub struct FakeTransport {
    script: Mutex<VecDeque<Result<HttpResponse, TransportError>>>,
    requests: Mutex<Vec<HttpRequest>>,
}

impl FakeTransport {
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            script: Mutex::default(),
            requests: Mutex::default(),
        })
    }

    pub fn push(&self, status: u16, headers: &[(&str, &str)], body: &[u8]) {
        self.script.lock().unwrap().push_back(Ok(HttpResponse {
            status,
            headers: headers
                .iter()
                .map(|(name, value)| Header {
                    name: (*name).into(),
                    value: (*value).into(),
                })
                .collect(),
            body: body.to_vec(),
        }));
    }

    pub fn push_network_error(&self, message: &str) {
        self.script.lock().unwrap().push_back(Err(TransportError {
            message: message.into(),
        }));
    }

    pub fn requests(&self) -> Vec<HttpRequest> {
        self.requests.lock().unwrap().clone()
    }
}

#[async_trait]
impl Transport for FakeTransport {
    async fn send(&self, request: HttpRequest) -> Result<HttpResponse, TransportError> {
        // Lets concurrent tests really interleave.
        tokio::task::yield_now().await;
        self.requests.lock().unwrap().push(request);
        self.script
            .lock()
            .unwrap()
            .pop_front()
            .expect("FakeTransport script is empty")
    }
}

/// Keeps the session in memory. `RefreshToken` is not `Clone`, so it is stored encoded.
pub struct FakeTokenStore {
    stored: Mutex<Option<(keygo_core::DeviceId, String, u64)>>,
    fail_saves: AtomicBool,
    fail_clears: AtomicBool,
}

impl FakeTokenStore {
    pub fn new() -> Self {
        Self {
            stored: Mutex::new(None),
            fail_saves: AtomicBool::new(false),
            fail_clears: AtomicBool::new(false),
        }
    }

    pub fn seed(&self, session: StoredSession) {
        *self.stored.lock().unwrap() = Some(encode(&session));
    }

    /// The stored refresh token in its encoded form.
    pub fn current_refresh_token(&self) -> Option<String> {
        self.stored.lock().unwrap().as_ref().map(|s| s.1.clone())
    }

    pub fn fail_saves(&self) {
        self.fail_saves.store(true, Ordering::SeqCst);
    }

    pub fn fail_clears(&self) {
        self.fail_clears.store(true, Ordering::SeqCst);
    }
}

impl Default for FakeTokenStore {
    fn default() -> Self {
        Self::new()
    }
}

fn encode(session: &StoredSession) -> (keygo_core::DeviceId, String, u64) {
    (
        session.device_id,
        session.refresh_token.expose_encoded().to_string(),
        session.refresh_expires_at,
    )
}

#[async_trait]
impl TokenStore for FakeTokenStore {
    async fn load(&self) -> Result<Option<StoredSession>, TokenStoreError> {
        Ok(self
            .stored
            .lock()
            .unwrap()
            .as_ref()
            .map(|(id, token, exp)| StoredSession {
                device_id: *id,
                refresh_token: token.parse().expect("stored token is valid"),
                refresh_expires_at: *exp,
            }))
    }

    async fn save(&self, session: StoredSession) -> Result<(), TokenStoreError> {
        if self.fail_saves.load(Ordering::SeqCst) {
            return Err(TokenStoreError {
                message: "save failed".into(),
            });
        }
        *self.stored.lock().unwrap() = Some(encode(&session));
        Ok(())
    }

    async fn clear(&self) -> Result<(), TokenStoreError> {
        if self.fail_clears.load(Ordering::SeqCst) {
            return Err(TokenStoreError {
                message: "clear failed".into(),
            });
        }
        *self.stored.lock().unwrap() = None;
        Ok(())
    }
}

/// The same key as [`TestSigner::new`].
pub struct FakeDpopKey(TestSigner);

impl FakeDpopKey {
    pub fn new() -> Self {
        Self(TestSigner::new())
    }
}

impl Default for FakeDpopKey {
    fn default() -> Self {
        Self::new()
    }
}

impl DpopKey for FakeDpopKey {
    fn public_key(&self) -> Vec<u8> {
        self.0.public_key_sec1()
    }

    fn sign(&self, message: Vec<u8>) -> Result<Vec<u8>, SignError> {
        Ok(self.0.sign_der(&message))
    }
}

pub struct FixedClock(AtomicU64);

impl FixedClock {
    pub fn new(now_secs: u64) -> Arc<Self> {
        Arc::new(Self(AtomicU64::new(now_secs)))
    }

    pub fn advance(&self, secs: u64) {
        self.0.fetch_add(secs, Ordering::SeqCst);
    }
}

impl Clock for FixedClock {
    fn now_secs(&self) -> u64 {
        self.0.load(Ordering::SeqCst)
    }
}
