use super::client::SyncClient;
use super::error::{SyncError, SyncResult, from_response};
use super::transport::{Header, HttpRequest, HttpResponse};
use keygo_api::{Auth, Endpoint};
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

impl SyncClient {
    pub(super) async fn exchange(&self, request: HttpRequest) -> SyncResult<HttpResponse> {
        self.transport
            .send(request)
            .await
            .map_err(|e| SyncError::Network { message: e.message })
    }

    pub(super) async fn call<E: Endpoint>(
        &self,
        endpoint: &E,
        body: &E::Request,
    ) -> SyncResult<E::Response> {
        match E::AUTH {
            Auth::None => {}
            // Replaced by the DPoP proof and access token paths in later tasks; never send these bare.
            Auth::Proof | Auth::AccessToken => {
                return Err(SyncError::Client {
                    message: "authenticated calls are not supported yet".into(),
                });
            }
        }

        let url = format!(
            "{}{}",
            self.base_url.as_str().trim_end_matches('/'),
            endpoint.path(),
        );
        let mut headers = Vec::new();
        let body = match encode_body(body)? {
            Some(bytes) => {
                headers.push(Header {
                    name: "content-type".into(),
                    value: "application/json".into(),
                });
                bytes
            }
            None => Vec::new(),
        };

        let response = self
            .exchange(HttpRequest {
                method: E::METHOD,
                url,
                headers,
                body,
            })
            .await?;

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
}
