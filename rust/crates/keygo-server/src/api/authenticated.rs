use crate::api::dpop::check_dpop;
use crate::api::{AppState, ErrorCode, HttpError, ServerError, single_header};
use crate::db::sessions;
use crate::db::sessions::SessionState;
use crate::domain::{Claims, RevokeReason};
use axum::extract::FromRequestParts;
use axum::http::header::{AUTHORIZATION, WWW_AUTHENTICATE};
use axum::http::request::Parts;
use axum::http::{HeaderMap, HeaderValue, StatusCode};
use keygo_dpop::{Bound, VerifyError};
use thiserror::Error;

/// Every rejection answers like a protected resource (RFC 9449 §7.1): `401` with a `DPoP`
/// challenge, whose `error` matches the body's `code` wherever the RFC has one.
#[derive(Debug, Error)]
pub enum AuthError {
    #[error("authentication required")]
    Missing,
    #[error("invalid access token")]
    Invalid,
    #[error("session revoked")]
    Revoked(Option<RevokeReason>),
    #[error(transparent)]
    Dpop(#[from] VerifyError),
}

impl HttpError for AuthError {
    fn code(&self) -> ErrorCode {
        match self {
            Self::Missing | Self::Invalid => ErrorCode::InvalidToken,
            Self::Revoked(_) => ErrorCode::SessionRevoked,
            Self::Dpop(err) => err.code(),
        }
    }

    fn status(&self) -> StatusCode {
        match self {
            // A full replay cache is not the client's fault, so it keeps its 429.
            Self::Dpop(err @ VerifyError::Busy { .. }) => err.status(),
            _ => StatusCode::UNAUTHORIZED,
        }
    }

    fn headers(&self) -> HeaderMap {
        let (mut headers, error) = match self {
            // A full replay cache is not an authentication failure: no challenge.
            Self::Dpop(err @ VerifyError::Busy { .. }) => return err.headers(),
            // No credentials at all: the bare challenge (RFC 6750 §3.1).
            Self::Missing => (HeaderMap::new(), None),
            // RFC 9449 has no code for a revoked session; to it the token is just invalid.
            Self::Invalid | Self::Revoked(_) => (HeaderMap::new(), Some("invalid_token")),
            Self::Dpop(err @ VerifyError::Invalid(_)) => {
                (err.headers(), Some("invalid_dpop_proof"))
            }
            Self::Dpop(err @ VerifyError::UseNonce(_)) => (err.headers(), Some("use_dpop_nonce")),
        };

        let challenge = match error {
            Some(error) => format!(r#"DPoP algs="ES256", error="{error}""#),
            None => r#"DPoP algs="ES256""#.to_owned(),
        };
        headers.insert(
            WWW_AUTHENTICATE,
            HeaderValue::try_from(challenge).expect("the challenge is ASCII"),
        );
        headers
    }

    fn reason(&self) -> Option<&'static str> {
        match self {
            Self::Revoked(reason) => reason.map(RevokeReason::as_str),
            _ => None,
        }
    }
}

pub struct Authenticated(Claims);

impl Authenticated {
    pub fn claims(&self) -> &Claims {
        &self.0
    }
}

impl FromRequestParts<AppState> for Authenticated {
    type Rejection = ServerError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let access_token = access_token(&parts.headers)?;
        let claims = state
            .token_issuer
            .verify(access_token)
            .map_err(|_| AuthError::Invalid)?;

        let bound = Bound {
            access_token,
            jkt: claims.jkt(),
        };
        let checked = check_dpop(parts, state, Some(bound)).map_err(AuthError::from)?;

        let mut conn = state.db.acquire().await?;
        match sessions::state(&mut conn, claims.session_id()).await? {
            SessionState::Live => {
                checked.record().map_err(AuthError::from)?;
                Ok(Self(claims))
            }
            SessionState::Expired => Err(AuthError::Invalid.into()),
            SessionState::Revoked(reason) => Err(AuthError::Revoked(reason).into()),
        }
    }
}

#[derive(Debug, Error)]
#[error("recent sign-in required")]
pub struct FreshAuthRequired;

impl HttpError for FreshAuthRequired {
    fn code(&self) -> ErrorCode {
        ErrorCode::FreshAuthRequired
    }
}

pub struct FreshAuthenticated(Authenticated);

impl FreshAuthenticated {
    pub fn claims(&self) -> &Claims {
        self.0.claims()
    }
}

impl FromRequestParts<AppState> for FreshAuthenticated {
    type Rejection = ServerError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let authenticated = Authenticated::from_request_parts(parts, state).await?;
        if !authenticated.claims().is_fresh() {
            return Err(FreshAuthRequired.into());
        }
        Ok(Self(authenticated))
    }
}

fn access_token(headers: &HeaderMap) -> Result<&str, AuthError> {
    if !headers.contains_key(AUTHORIZATION) {
        return Err(AuthError::Missing);
    }

    let authorization = single_header(headers, AUTHORIZATION)
        .and_then(|value| value.to_str().ok())
        .ok_or(AuthError::Invalid)?;

    match authorization.split_once(' ') {
        Some((scheme, token)) if scheme.eq_ignore_ascii_case("DPoP") => Ok(token),
        _ => Err(AuthError::Invalid),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::FRESH_AUTH_WINDOW;
    use crate::service::session;
    use crate::testing::{app_state, insert_device, issue_session, prover};
    use axum::Router;
    use axum::body::Body;
    use axum::http::Request;
    use axum::routing::get;
    use http_body_util::BodyExt;
    use keygo_dpop::testing::TestSigner;
    use keygo_dpop::{DpopProver, Htm, ProofRequest};
    use keygo_opaque::server::ServerSetup;
    use serde_json::Value;
    use sqlx::PgPool;
    use std::time::Duration;
    use tower::ServiceExt;

    const URI: &str = "/protected";
    const FRESH_URI: &str = "/sensitive";

    async fn send(state: &AppState, request: Request<Body>) -> (StatusCode, HeaderMap, Value) {
        let app = Router::new()
            .route(URI, get(|_: Authenticated| async { "ok" }))
            .route(FRESH_URI, get(|_: FreshAuthenticated| async { "ok" }))
            .with_state(state.clone());
        let response = app.oneshot(request).await.unwrap();
        let status = response.status();
        let headers = response.headers().clone();
        let body = response.into_body().collect().await.unwrap().to_bytes();
        (
            status,
            headers,
            serde_json::from_slice(&body).unwrap_or(Value::Null),
        )
    }

    fn request(
        state: &AppState,
        prover: &DpopProver<TestSigner>,
        authorization: &str,
        access_token: &str,
        nonce: Option<&str>,
    ) -> Request<Body> {
        request_to(state, URI, prover, authorization, access_token, nonce)
    }

    fn request_to(
        state: &AppState,
        uri: &str,
        prover: &DpopProver<TestSigner>,
        authorization: &str,
        access_token: &str,
        nonce: Option<&str>,
    ) -> Request<Body> {
        let htu = state.public_url.join(uri).unwrap();
        let proof = prover
            .proof(&ProofRequest {
                htm: Htm::Get,
                htu: htu.as_str(),
                access_token: Some(access_token),
                nonce,
            })
            .unwrap();
        Request::get(uri)
            .header(AUTHORIZATION, authorization)
            .header("dpop", proof)
            .body(Body::empty())
            .unwrap()
    }

    /// Sends with a fresh proof and answers a nonce challenge once, like a real client.
    async fn call(
        state: &AppState,
        prover: &DpopProver<TestSigner>,
        access_token: &str,
    ) -> (StatusCode, HeaderMap, Value) {
        call_to(state, URI, prover, access_token).await
    }

    async fn call_to(
        state: &AppState,
        uri: &str,
        prover: &DpopProver<TestSigner>,
        access_token: &str,
    ) -> (StatusCode, HeaderMap, Value) {
        let authorization = format!("DPoP {access_token}");
        let first = request_to(state, uri, prover, &authorization, access_token, None);
        let (status, headers, body) = send(state, first).await;
        if body["code"] != "use_dpop_nonce" {
            return (status, headers, body);
        }
        let nonce = headers["dpop-nonce"].to_str().unwrap();
        let retry = request_to(
            state,
            uri,
            prover,
            &authorization,
            access_token,
            Some(nonce),
        );
        send(state, retry).await
    }

    /// An access token for the same session whose sign-in lies past the freshness window.
    fn stale_token(issued: &session::Issued, prover: &DpopProver<TestSigner>) -> String {
        let issuer = crate::testing::token_issuer();
        let claims = issuer.verify(issued.access_token.expose()).unwrap();
        let auth_time = crate::domain::unix_now() - FRESH_AUTH_WINDOW.as_secs() - 1;
        issuer
            .issue(
                claims.user_id(),
                claims.session_id(),
                auth_time,
                prover.jkt(),
            )
            .expose()
            .to_owned()
    }

    async fn signed_in(db: &PgPool, prover: &DpopProver<TestSigner>) -> session::Issued {
        let (user_id, device_id) = insert_device(db).await;
        issue_session(db, user_id, device_id, prover.jkt()).await
    }

    fn challenge(headers: &HeaderMap) -> &str {
        headers[WWW_AUTHENTICATE].to_str().unwrap()
    }

    #[sqlx::test]
    async fn live_session_is_authenticated(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let issued = signed_in(&db, &prover).await;

        let (status, _, _) = call(&state, &prover, issued.access_token.expose()).await;

        assert_eq!(status, StatusCode::OK);
    }

    #[sqlx::test]
    async fn missing_credentials_get_the_bare_challenge(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());

        let request = Request::get(URI).body(Body::empty()).unwrap();
        let (status, headers, body) = send(&state, request).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_token");
        assert_eq!(challenge(&headers), r#"DPoP algs="ES256""#);
    }

    #[sqlx::test]
    async fn bearer_scheme_is_an_invalid_token(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let issued = signed_in(&db, &prover).await;
        let token = issued.access_token.expose();

        let request = request(&state, &prover, &format!("Bearer {token}"), token, None);
        let (status, headers, body) = send(&state, request).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_token");
        assert_eq!(
            challenge(&headers),
            r#"DPoP algs="ES256", error="invalid_token""#
        );
    }

    #[sqlx::test]
    async fn two_authorization_headers_are_an_invalid_token(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());

        let request = Request::get(URI)
            .header(AUTHORIZATION, "DPoP a")
            .header(AUTHORIZATION, "DPoP b")
            .body(Body::empty())
            .unwrap();
        let (status, _, body) = send(&state, request).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_token");
    }

    #[sqlx::test]
    async fn proof_from_another_key_is_unauthorized(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let issued = signed_in(&db, &prover()).await;
        let other = DpopProver::new(TestSigner::from_seed(8));

        let (status, headers, body) = call(&state, &other, issued.access_token.expose()).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_dpop_proof");
        assert_eq!(
            challenge(&headers),
            r#"DPoP algs="ES256", error="invalid_dpop_proof""#
        );
    }

    #[sqlx::test]
    async fn replayed_proof_is_unauthorized(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let issued = signed_in(&db, &prover).await;
        let token = issued.access_token.expose();
        let authorization = format!("DPoP {token}");

        let first = request(&state, &prover, &authorization, token, None);
        let (_, headers, _) = send(&state, first).await;
        let nonce = headers["dpop-nonce"].to_str().unwrap();
        let proof = prover
            .proof(&ProofRequest {
                htm: Htm::Get,
                htu: state.public_url.join(URI).unwrap().as_str(),
                access_token: Some(token),
                nonce: Some(nonce),
            })
            .unwrap();
        let replay = || {
            Request::get(URI)
                .header(AUTHORIZATION, &authorization)
                .header("dpop", &proof)
                .body(Body::empty())
                .unwrap()
        };

        let (accepted, _, _) = send(&state, replay()).await;
        let (status, headers, body) = send(&state, replay()).await;

        assert_eq!(accepted, StatusCode::OK);
        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_dpop_proof");
        assert_eq!(
            challenge(&headers),
            r#"DPoP algs="ES256", error="invalid_dpop_proof""#
        );
    }

    #[sqlx::test]
    async fn nonce_challenge_is_unauthorized_with_a_nonce(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let issued = signed_in(&db, &prover).await;
        let token = issued.access_token.expose();

        // A verifier that just started accepts no proof without one of its own nonces.
        let request = request(&state, &prover, &format!("DPoP {token}"), token, None);
        let (status, headers, body) = send(&state, request).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "use_dpop_nonce");
        assert!(headers.contains_key("dpop-nonce"));
        assert_eq!(
            challenge(&headers),
            r#"DPoP algs="ES256", error="use_dpop_nonce""#
        );
    }

    #[sqlx::test]
    async fn revoked_session_answers_its_reason(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let issued = signed_in(&db, &prover).await;
        let mut conn = db.acquire().await.unwrap();
        sessions::revoke_by_device(&mut conn, issued.device_id, RevokeReason::Replaced)
            .await
            .unwrap();

        let (status, headers, body) = call(&state, &prover, issued.access_token.expose()).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "session_revoked");
        assert_eq!(body["reason"], "replaced");
        assert_eq!(
            challenge(&headers),
            r#"DPoP algs="ES256", error="invalid_token""#
        );
    }

    #[sqlx::test]
    async fn deleted_session_is_revoked_without_a_reason(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let issued = signed_in(&db, &prover).await;
        sqlx::query("delete from devices")
            .execute(&db)
            .await
            .unwrap();

        let (status, _, body) = call(&state, &prover, issued.access_token.expose()).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "session_revoked");
        assert!(body.get("reason").is_none());
    }

    #[tokio::test]
    async fn full_replay_cache_keeps_its_429() {
        let busy = AuthError::Dpop(VerifyError::Busy {
            retry_after: Duration::from_secs(7),
        });

        let headers = busy.headers();
        assert_eq!(headers[axum::http::header::RETRY_AFTER], "7");
        assert!(!headers.contains_key(WWW_AUTHENTICATE));
        let (status, body) = crate::api::error::render(busy).await;
        assert_eq!(status, StatusCode::TOO_MANY_REQUESTS);
        assert_eq!(body["code"], "rate_limited");
    }

    #[sqlx::test]
    async fn session_past_its_cap_is_an_invalid_token(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let issued = signed_in(&db, &prover).await;
        crate::testing::expire(&db, "sessions").await;

        let (status, headers, body) = call(&state, &prover, issued.access_token.expose()).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_token");
        assert_eq!(
            challenge(&headers),
            r#"DPoP algs="ES256", error="invalid_token""#
        );
    }

    #[sqlx::test]
    async fn fresh_token_reaches_a_sensitive_endpoint(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let issued = signed_in(&db, &prover).await;

        let (status, _, _) =
            call_to(&state, FRESH_URI, &prover, issued.access_token.expose()).await;

        assert_eq!(status, StatusCode::OK);
    }

    #[sqlx::test]
    async fn stale_token_is_forbidden_without_a_challenge(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let issued = signed_in(&db, &prover).await;
        let stale = stale_token(&issued, &prover);

        let (status, headers, body) = call_to(&state, FRESH_URI, &prover, &stale).await;

        assert_eq!(status, StatusCode::FORBIDDEN);
        assert_eq!(body["code"], "fresh_auth_required");
        assert!(!headers.contains_key(WWW_AUTHENTICATE));
    }

    #[sqlx::test]
    async fn stale_token_still_reaches_a_plain_endpoint(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let issued = signed_in(&db, &prover).await;
        let stale = stale_token(&issued, &prover);

        let (status, _, _) = call(&state, &prover, &stale).await;

        assert_eq!(status, StatusCode::OK);
    }

    #[sqlx::test]
    async fn revocation_is_checked_before_freshness(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let issued = signed_in(&db, &prover).await;
        let stale = stale_token(&issued, &prover);
        let mut conn = db.acquire().await.unwrap();
        sessions::revoke_by_device(&mut conn, issued.device_id, RevokeReason::Replaced)
            .await
            .unwrap();

        let (status, _, body) = call_to(&state, FRESH_URI, &prover, &stale).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "session_revoked");
        assert_eq!(body["reason"], "replaced");
    }
}
