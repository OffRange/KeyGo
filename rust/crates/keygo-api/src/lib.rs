#![forbid(unsafe_code)]

pub mod auth;
pub mod devices;
pub mod domain;
mod endpoint;
pub mod endpoints;
mod error;
pub mod meta;

pub use endpoint::{Auth, Endpoint, Method};
pub use error::{ApiError, ErrorCode};
