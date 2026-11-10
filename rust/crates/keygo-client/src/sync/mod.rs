//! The sync client: the auth protocol over three host-supplied dependencies
//! ([`Transport`], [`SharedSigner`], [`TokenStore`]). No HTTP stack lives here.

mod client;
mod error;
mod pipeline;
mod signer;
mod state;
#[cfg(any(test, feature = "test-support"))]
pub mod testing;
mod tokens;
mod transport;

pub use client::{Account, SyncClient};
pub use error::{SyncError, SyncResult};
pub use signer::{SharedSigner, SignError};
pub use state::{Clock, SystemClock};
pub use tokens::{StoredSession, TokenStore, TokenStoreError};
pub use transport::{Header, HttpRequest, HttpResponse, Transport, TransportError};
