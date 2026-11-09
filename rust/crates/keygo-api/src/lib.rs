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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_endpoint_is_listed() {
        let declared = include_str!("endpoints.rs")
            .matches("impl Endpoint for ")
            .count();
        assert_eq!(endpoints::all_routes().len(), declared);
    }
}
