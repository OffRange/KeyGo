//! Credit-card field helpers, split by concern:
//!
//! - network detection — the issuing [`CardNetwork`] and its metadata (valid
//!   lengths, CVV length, grouping convention);
//! - number handling — parse, cap, group, and validate a PAN via [`Card`];
//! - expiration formatting — render an `MM/YY` date as it is typed or deleted.
//!
//! All entry points accept "dirty" input — spaces, dashes, and any other
//! non-digit characters are ignored — so they work equally well on a pasted
//! value or on text being typed live into a field.
//!
//! ```
//! use lib::card::{Card, CardNetwork};
//!
//! let card = Card::parse("378282246310005");
//! assert_eq!(card.network, CardNetwork::Amex);
//! assert_eq!(card.formatted, "3782 822463 10005");
//! assert!(card.is_valid());
//! ```

mod expiration;
mod network;
mod number;

pub use expiration::format_expiration_after_edit;
pub use network::CardNetwork;
pub use number::Card;

/// The longest PAN any supported network issues; the cap of last resort while
/// the network is still [`CardNetwork::Unknown`].
pub const MAX_PAN_DIGITS: usize = 19;

/// Strip everything but ASCII digits. Shared by every submodule, since they all
/// accept "dirty" input.
fn digits_only(s: &str) -> String {
    s.chars().filter(char::is_ascii_digit).collect()
}
