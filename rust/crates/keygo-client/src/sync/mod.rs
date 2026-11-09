//! The sync client: the auth protocol over three host-supplied traits
//! ([`Transport`], [`DpopKey`], [`TokenStore`]). No HTTP stack lives here.

mod client;
mod error;
mod pipeline;
mod signer;
mod state;
#[cfg(any(test, feature = "test-support"))]
pub mod testing;
mod tokens;
mod transport;

pub use client::SyncClient;
pub use error::{SyncError, SyncResult};
pub use signer::{DpopKey, ForeignSigner, SignError};
pub use state::{Clock, SystemClock};
pub use tokens::{StoredSession, TokenStore, TokenStoreError};
pub use transport::{Header, HttpRequest, HttpResponse, Transport, TransportError};
