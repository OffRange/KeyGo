use opaque_ke::errors::{InternalError, ProtocolError};
use thiserror::Error;

#[derive(Debug, Error, PartialEq, Eq)]
pub enum OpaqueError {
    #[error("Login failed: wrong password or unknown account")]
    InvalidLogin,
    #[error("Server reflected the client's blinded element")]
    ReflectedValue,
    #[error("Malformed OPAQUE message")]
    Malformed,
    #[error("Password must not be empty")]
    EmptyPassword,
    #[error("OPAQUE protocol failure: {0}")]
    Protocol(String),
}

pub type OpaqueResult<T> = Result<T, OpaqueError>;

impl From<ProtocolError> for OpaqueError {
    fn from(err: ProtocolError) -> Self {
        match err {
            ProtocolError::InvalidLoginError => Self::InvalidLogin,
            ProtocolError::ReflectedValueError => Self::ReflectedValue,
            ProtocolError::SerializationError
            | ProtocolError::SizeError { .. }
            | ProtocolError::LibraryError(InternalError::OprfError(
                voprf::Error::Deserialization,
            )) => Self::Malformed,
            other => Self::Protocol(format!("{other:?}")),
        }
    }
}
