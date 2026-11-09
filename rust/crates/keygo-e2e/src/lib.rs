//! End-to-end tests: the real `SyncClient` against the real `keygo-server` router, with real
//! OPAQUE and real DPoP. This is the only crate that builds both sides together, so it is where
//! the shipped server's feature set (no `crypto`) stays untouched by the client's.
