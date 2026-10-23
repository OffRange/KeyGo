use crate::api::{AppState, ErrorCode, HttpError, ServerError};
use axum::extract::{FromRequestParts, OriginalUri};
use axum::http::request::Parts;
use axum::http::{HeaderMap, HeaderName, HeaderValue, Method, StatusCode};
use keygo_dpop::Htm;
use keygo_dpop::verify::{Expected, InvalidProof, VerifyError};
use url::Url;

const DPOP: HeaderName = HeaderName::from_static("dpop");
const DPOP_NONCE: HeaderName = HeaderName::from_static("dpop-nonce");

pub struct DpopProof {
    pub jkt: [u8; 32],
}

impl FromRequestParts<AppState> for DpopProof {
    type Rejection = ServerError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let proof = single_header(&parts.headers, &DPOP)
            .and_then(|value| value.to_str().ok())
            .ok_or(VerifyError::Invalid(InvalidProof::Malformed))?;
        let htm = htm(&parts.method).ok_or(VerifyError::Invalid(InvalidProof::Request))?;
        let htu = expected_htu(parts, &state.public_url);

        let expected = Expected {
            htm,
            htu: htu.as_str(),
            access_token: None,
            jkt: None,
        };
        let jkt = state
            .dpop_verifier
            .verify(proof, expected)
            .inspect_err(|err| tracing::debug!(?err, "dpop proof rejected"))?;
        Ok(DpopProof { jkt })
    }
}

impl HttpError for VerifyError {
    fn status_and_code(&self) -> (StatusCode, ErrorCode) {
        match self {
            Self::Invalid(_) => (StatusCode::BAD_REQUEST, ErrorCode::InvalidDpopProof),
            Self::UseNonce(_) => (StatusCode::BAD_REQUEST, ErrorCode::UseDpopNonce),
            Self::Busy => (StatusCode::TOO_MANY_REQUESTS, ErrorCode::RateLimited),
        }
    }

    fn headers(&self) -> HeaderMap {
        match self {
            Self::UseNonce(nonce) => {
                let nonce = HeaderValue::try_from(nonce.to_string()).expect("nonces are base64url");
                HeaderMap::from_iter([(DPOP_NONCE, nonce)])
            }
            Self::Invalid(_) | Self::Busy => HeaderMap::new(),
        }
    }
}

fn single_header<'a>(headers: &'a HeaderMap, name: &HeaderName) -> Option<&'a HeaderValue> {
    let mut values = headers.get_all(name).iter();
    match (values.next(), values.next()) {
        (Some(value), None) => Some(value),
        _ => None,
    }
}

fn htm(method: &Method) -> Option<Htm> {
    match *method {
        Method::GET => Some(Htm::Get),
        Method::POST => Some(Htm::Post),
        Method::DELETE => Some(Htm::Delete),
        _ => None,
    }
}

fn expected_htu(parts: &Parts, public_url: &Url) -> Url {
    let path = parts
        .extensions
        .get::<OriginalUri>()
        .map_or(parts.uri.path(), |OriginalUri(uri)| uri.path());

    let mut url = public_url.clone();
    url.set_path(path);
    url.set_query(None);
    url.set_fragment(None);
    url
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::api::error::render;
    use axum::Router;
    use axum::body::Body;
    use axum::http::Request;
    use axum::routing::post;
    use serde_json::json;
    use tower::ServiceExt;

    fn public_url() -> Url {
        Url::parse("https://sync.example").unwrap()
    }

    async fn finish(request: Request<Body>) -> StatusCode {
        let state = AppState {
            public_url: public_url(),
            ..crate::api::tests::state()
        };
        let app = Router::new()
            .nest(
                "/auth",
                Router::new().route("/finish", post(|_: DpopProof| async {})),
            )
            .with_state(state);
        app.oneshot(request).await.unwrap().status()
    }

    #[test]
    fn htu_uses_public_url_and_the_unnested_path() {
        let (parts, ()) = Request::post("/finish")
            .extension(OriginalUri(
                "/api/v1/auth/login/finish?a=b".parse().unwrap(),
            ))
            .body(())
            .unwrap()
            .into_parts();

        assert_eq!(
            expected_htu(&parts, &public_url()).as_str(),
            "https://sync.example/api/v1/auth/login/finish",
        );
    }

    #[tokio::test]
    async fn missing_or_repeated_header_is_an_invalid_proof() {
        let missing = Request::post("/auth/finish").body(Body::empty()).unwrap();
        let repeated = Request::post("/auth/finish")
            .header(DPOP, "a.b.c")
            .header(DPOP, "a.b.c")
            .body(Body::empty())
            .unwrap();

        assert_eq!(finish(missing).await, StatusCode::BAD_REQUEST);
        assert_eq!(finish(repeated).await, StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn invalid_proof_hides_its_reason() {
        let (status, body) = render(VerifyError::from(InvalidProof::Replay)).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(
            body,
            json!({ "code": "invalid_dpop_proof", "message": "invalid dpop proof" }),
        );
    }

    #[tokio::test]
    async fn full_replay_cache_is_rate_limited() {
        let (status, body) = render(VerifyError::Busy).await;

        assert_eq!(status, StatusCode::TOO_MANY_REQUESTS);
        assert_eq!(body["code"], "rate_limited");
    }
}
