use async_trait::async_trait;
use keygo_api::Method;
use std::fmt;

#[derive(Clone, PartialEq, Eq)]
pub struct Header {
    pub name: String,
    pub value: String,
}

#[derive(Clone, PartialEq, Eq)]
pub struct HttpRequest {
    pub method: Method,
    pub url: String,
    pub headers: Vec<Header>,
    pub body: Vec<u8>,
}

#[derive(Clone, PartialEq, Eq)]
pub struct HttpResponse {
    pub status: u16,
    pub headers: Vec<Header>,
    pub body: Vec<u8>,
}

// Header values hold proofs and access tokens, and bodies hold refresh tokens, so none of these
// print them.
impl fmt::Debug for Header {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Header")
            .field("name", &self.name)
            .field("value", &format_args!("<redacted>"))
            .finish()
    }
}

impl fmt::Debug for HttpRequest {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("HttpRequest")
            .field("method", &self.method)
            .field("url", &self.url)
            .field("headers", &header_names(&self.headers))
            .field("body_len", &self.body.len())
            .finish()
    }
}

impl fmt::Debug for HttpResponse {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("HttpResponse")
            .field("status", &self.status)
            .field("headers", &header_names(&self.headers))
            .field("body_len", &self.body.len())
            .finish()
    }
}

fn header_names(headers: &[Header]) -> Vec<&str> {
    headers.iter().map(|h| h.name.as_str()).collect()
}

#[derive(Debug, thiserror::Error)]
#[error("{message}")]
pub struct TransportError {
    pub message: String,
}

/// The host owns TLS, timeouts and pooling.
/// It must not retry, follow auth challenges or log bodies.
#[async_trait]
pub trait Transport: Send + Sync {
    async fn send(&self, request: HttpRequest) -> Result<HttpResponse, TransportError>;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn debug_output_hides_tokens_and_bodies() {
        let request = HttpRequest {
            method: Method::Post,
            url: "https://kg.example/api/v1/auth/token".into(),
            headers: vec![Header {
                name: "authorization".into(),
                value: "DPoP secret-access-token".into(),
            }],
            body: br#"{"refresh_token":"secret-refresh-token"}"#.to_vec(),
        };
        let response = HttpResponse {
            status: 200,
            headers: request.headers.clone(),
            body: request.body.clone(),
        };

        for output in [format!("{request:?}"), format!("{response:?}")] {
            assert!(!output.contains("secret-access-token"), "{output}");
            assert!(!output.contains("secret-refresh-token"), "{output}");
            assert!(output.contains("authorization"), "{output}");
            assert!(
                output.contains(&format!("body_len: {}", request.body.len())),
                "{output}",
            );
        }
        let header = format!("{:?}", request.headers[0]);
        assert!(!header.contains("secret-access-token"), "{header}");
        assert!(header.contains("<redacted>"), "{header}");
    }
}
