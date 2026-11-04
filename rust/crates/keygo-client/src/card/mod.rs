mod expiration;
mod network;
mod number;

pub use expiration::format_expiration_after_edit;
pub use network::CardNetwork;
pub use number::Card;

pub const MAX_PAN_DIGITS: usize = 19;

fn digits_only(s: &str) -> String {
    s.chars().filter(char::is_ascii_digit).collect()
}
