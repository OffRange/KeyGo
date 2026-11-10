use serde::de::DeserializeOwned;
use serde::Serialize;
use std::borrow::Cow;

/// What a request must carry for the server to accept it.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Auth {
    None,
    /// A DPoP proof, but no access token.
    Proof,
    /// An access token and the DPoP proof it is bound to.
    AccessToken,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Method {
    Get,
    Post,
    Delete,
}

impl Method {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Get => "GET",
            Self::Post => "POST",
            Self::Delete => "DELETE",
        }
    }
}

pub trait Endpoint {
    /// `()` when the route takes no body.
    type Request: Serialize + DeserializeOwned;
    /// `()` when the route answers 204.
    type Response: Serialize + DeserializeOwned;
    const METHOD: Method;
    const AUTH: Auth;

    fn path(&self) -> Cow<'static, str>;
}
