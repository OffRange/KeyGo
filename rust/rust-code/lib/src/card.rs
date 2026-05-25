//! A credit-card number formatter.
//!
//! Detects the card network from the leading digits (the IIN/BIN), groups the
//! number into the spacing convention used by that network, and offers a Luhn
//! checksum check.
//!
//! All public entry points accept "dirty" input — spaces, dashes, and any
//! other non-digit characters are ignored — so they work equally well on a
//! pasted number or on text being typed live into a field.
//!
//! ```
//! use lib::card::{Card, CardNetwork};
//!
//! let card = Card::parse("378282246310005");
//! assert_eq!(card.network, CardNetwork::Amex);
//! assert_eq!(card.formatted, "3782 822463 10005");
//! assert!(card.is_valid());
//! ```

use std::fmt;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CardNetwork {
    Visa,
    Mastercard,
    Amex,
    Discover,
    DinersClub,
    Jcb,
    UnionPay,
    Maestro,
    Unknown,
}

impl CardNetwork {
    pub fn detect(number: &str) -> CardNetwork {
        detect_digits(&digits_only(number))
    }

    pub fn name(self) -> &'static str {
        match self {
            CardNetwork::Visa => "Visa",
            CardNetwork::Mastercard => "Mastercard",
            CardNetwork::Amex => "American Express",
            CardNetwork::Discover => "Discover",
            CardNetwork::DinersClub => "Diners Club",
            CardNetwork::Jcb => "JCB",
            CardNetwork::UnionPay => "UnionPay",
            CardNetwork::Maestro => "Maestro",
            CardNetwork::Unknown => "Unknown",
        }
    }

    pub fn valid_lengths(self) -> &'static [usize] {
        match self {
            CardNetwork::Visa => &[13, 16, 19],
            CardNetwork::Mastercard => &[16],
            CardNetwork::Amex => &[15],
            CardNetwork::Discover => &[16, 17, 18, 19],
            CardNetwork::DinersClub => &[14, 16],
            CardNetwork::Jcb => &[16, 17, 18, 19],
            CardNetwork::UnionPay => &[16, 17, 18, 19],
            CardNetwork::Maestro => &[12, 13, 14, 15, 16, 17, 18, 19],
            CardNetwork::Unknown => &[],
        }
    }

    /// The longest number this network issues. Unknown networks fall back to the global
    /// [`MAX_PAN_DIGITS`].
    pub fn max_len(self) -> usize {
        self.valid_lengths()
            .iter()
            .copied()
            .max()
            .unwrap_or(MAX_PAN_DIGITS)
    }

    pub fn cvv_len(self) -> usize {
        match self {
            CardNetwork::Unknown | CardNetwork::Amex => 4,
            _ => 3,
        }
    }

    /// Group sizes used to space a number of `len` digits.
    fn groups(self, len: usize) -> Vec<usize> {
        match self {
            CardNetwork::Amex => fit_pattern(&[4, 6, 5], len),
            CardNetwork::DinersClub => fit_pattern(&[4, 6, 4], len),
            _ => groups_of_four(len),
        }
    }
}

#[derive(Debug, Clone)]
pub struct Card {
    pub network: CardNetwork,
    pub digits: String,
    pub formatted: String,
}

impl Card {
    pub fn parse(input: &str) -> Card {
        let digits = digits_only(input);
        let network = detect_digits(&digits);
        let formatted = group(&digits, &network.groups(digits.len()));
        Card {
            network,
            digits,
            formatted,
        }
    }

    pub fn is_length_valid(&self) -> bool {
        self.network.valid_lengths().contains(&self.digits.len())
    }

    pub fn is_luhn_valid(&self) -> bool {
        luhn_valid(&self.digits)
    }

    pub fn is_valid(&self) -> bool {
        self.network != CardNetwork::Unknown && self.is_length_valid() && self.is_luhn_valid()
    }
}

impl fmt::Display for Card {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.formatted)
    }
}

pub const MAX_PAN_DIGITS: usize = 19;

/// Strip everything but digits and cap to the detected network's
/// [`CardNetwork::max_len`] (falling back to [`MAX_PAN_DIGITS`] while unknown).
/// This is the raw value a field should *store*; grouping is purely visual.
pub fn card_digits(number: &str) -> String {
    let all_digits = digits_only(number);
    let max = detect_digits(&all_digits).max_len();
    all_digits.chars().take(max).collect()
}

/// Format a card number into network-appropriate, space-separated groups.
/// Input is cleaned and capped via [`card_digits`] first.
pub fn format_card_number(number: &str) -> String {
    let digits = card_digits(number);
    let network = detect_digits(&digits);
    group(&digits, &network.groups(digits.len()))
}

/// Digit offsets at which a grouping space should be inserted for display,
/// e.g. `[4, 8, 12]` for a 16-digit number.
pub fn card_space_indices(number: &str) -> Vec<usize> {
    let digits = card_digits(number);
    let network = detect_digits(&digits);
    let mut indices = Vec::new();
    let mut pos = 0;
    for size in network.groups(digits.len()) {
        pos += size;
        if pos < digits.len() {
            indices.push(pos);
        }
    }
    indices
}

pub fn luhn_valid(number: &str) -> bool {
    let digits = digits_only(number);
    if digits.is_empty() {
        return false;
    }
    let sum: u32 = digits
        .bytes()
        .rev()
        .enumerate()
        .map(|(i, b)| {
            let d = u32::from(b - b'0');
            if i % 2 == 1 {
                let doubled = d * 2;
                if doubled > 9 { doubled - 9 } else { doubled }
            } else {
                d
            }
        })
        .sum();
    sum.is_multiple_of(10)
}

// --- internal helpers -------------------------------------------------------

fn digits_only(s: &str) -> String {
    s.chars().filter(char::is_ascii_digit).collect()
}

fn prefix(digits: &str, n: usize) -> Option<u32> {
    digits.get(..n).and_then(|p| p.parse().ok())
}

/// Network detection by IIN range. More specific ranges are checked before
/// broader ones so overlapping prefixes resolve correctly.
fn detect_digits(d: &str) -> CardNetwork {
    use CardNetwork::*;
    if d.is_empty() {
        return Unknown;
    }

    // American Express: 34, 37
    if matches!(prefix(d, 2), Some(34 | 37)) {
        return Amex;
    }
    // JCB: 3528–3589 (checked before the broader Diners "3" ranges)
    if matches!(prefix(d, 4), Some(3528..=3589)) {
        return Jcb;
    }
    // Diners Club: 300–305, 36, 38, 39
    if matches!(prefix(d, 3), Some(300..=305)) || matches!(prefix(d, 2), Some(36 | 38 | 39)) {
        return DinersClub;
    }
    // Visa: 4
    if d.starts_with('4') {
        return Visa;
    }
    // Mastercard: 51–55, 2221–2720
    if matches!(prefix(d, 2), Some(51..=55)) || matches!(prefix(d, 4), Some(2221..=2720)) {
        return Mastercard;
    }
    // Discover: 6011, 644–649, 65, and the 622126–622925 co-brand range
    // (checked before UnionPay's broad "62").
    if matches!(prefix(d, 4), Some(6011))
        || matches!(prefix(d, 3), Some(644..=649))
        || matches!(prefix(d, 2), Some(65))
        || matches!(prefix(d, 6), Some(622126..=622925))
    {
        return Discover;
    }
    // UnionPay: 62, 81
    if matches!(prefix(d, 2), Some(62 | 81)) {
        return UnionPay;
    }
    // Maestro: a selection of the common debit ranges
    if matches!(prefix(d, 2), Some(50 | 56 | 57 | 58 | 63 | 67 | 69)) {
        return Maestro;
    }

    Unknown
}

/// Truncate a fixed grouping `pattern` (e.g. Amex's `[4, 6, 5]`) to cover exactly
/// `len` digits, so a partially typed number is spaced the same way it will be once
/// complete. Any digits beyond the pattern trail in fours as a safety net.
fn fit_pattern(pattern: &[usize], len: usize) -> Vec<usize> {
    let mut groups = Vec::new();
    let mut remaining = len;
    for &size in pattern {
        if remaining == 0 {
            break;
        }
        let take = size.min(remaining);
        groups.push(take);
        remaining -= take;
    }
    if remaining > 0 {
        groups.extend(groups_of_four(remaining));
    }
    groups
}

fn groups_of_four(len: usize) -> Vec<usize> {
    let mut groups = Vec::new();
    let mut remaining = len;
    while remaining > 4 {
        groups.push(4);
        remaining -= 4;
    }
    if remaining > 0 {
        groups.push(remaining);
    }
    groups
}

fn group(digits: &str, sizes: &[usize]) -> String {
    let bytes = digits.as_bytes();
    let mut out = String::with_capacity(digits.len() + sizes.len());
    let mut idx = 0;

    for &size in sizes {
        if idx >= bytes.len() {
            break;
        }
        if idx > 0 {
            out.push(' ');
        }
        let end = (idx + size).min(bytes.len());
        out.push_str(&digits[idx..end]);
        idx = end;
    }

    // Safety net for any digits beyond the declared pattern.
    while idx < bytes.len() {
        out.push(' ');
        let end = (idx + 4).min(bytes.len());
        out.push_str(&digits[idx..end]);
        idx = end;
    }

    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn detects_networks() {
        assert_eq!(CardNetwork::detect("4111111111111111"), CardNetwork::Visa);
        assert_eq!(
            CardNetwork::detect("5500000000000004"),
            CardNetwork::Mastercard
        );
        assert_eq!(
            CardNetwork::detect("2221000000000009"),
            CardNetwork::Mastercard
        );
        assert_eq!(CardNetwork::detect("378282246310005"), CardNetwork::Amex);
        assert_eq!(
            CardNetwork::detect("6011000990139424"),
            CardNetwork::Discover
        );
        assert_eq!(
            CardNetwork::detect("30569309025904"),
            CardNetwork::DinersClub
        );
        assert_eq!(CardNetwork::detect("3530111333300000"), CardNetwork::Jcb);
        assert_eq!(
            CardNetwork::detect("6212345678901232"),
            CardNetwork::UnionPay
        );
        assert_eq!(CardNetwork::detect(""), CardNetwork::Unknown);
        assert_eq!(
            CardNetwork::detect("9999999999999999"),
            CardNetwork::Unknown
        );
    }

    #[test]
    fn jcb_beats_diners_in_3500_range() {
        // 3530 is JCB even though "3x" is otherwise Diners territory.
        assert_eq!(CardNetwork::detect("3530111333300000"), CardNetwork::Jcb);
    }

    #[test]
    fn formats_by_network() {
        assert_eq!(
            format_card_number("4111111111111111"),
            "4111 1111 1111 1111"
        );
        assert_eq!(format_card_number("378282246310005"), "3782 822463 10005");
        assert_eq!(format_card_number("30569309025904"), "3056 930902 5904");
    }

    #[test]
    fn ignores_existing_separators() {
        assert_eq!(
            format_card_number("5500-0000 0000_0004"),
            "5500 0000 0000 0004"
        );
    }

    #[test]
    fn formats_partial_input_as_typed() {
        assert_eq!(format_card_number("41111"), "4111 1");
        assert_eq!(format_card_number("411111111"), "4111 1111 1");
    }

    #[test]
    fn amex_groups_progressively_while_typing() {
        // Prefix already identifies Amex, so partial input uses 4-6-5, not fours.
        assert_eq!(format_card_number("341234"), "3412 34");
        assert_eq!(format_card_number("3412345678"), "3412 345678");
        assert_eq!(format_card_number("34123456789012"), "3412 345678 9012");
        assert_eq!(format_card_number("341234567890123"), "3412 345678 90123");
        assert_eq!(card_space_indices("3412345678"), vec![4]);
        assert_eq!(card_space_indices("34123456789012"), vec![4, 10]);
    }

    #[test]
    fn nineteen_digits_trail_in_fours() {
        assert_eq!(
            format_card_number("4111111111111111123"),
            "4111 1111 1111 1111 123"
        );
    }

    #[test]
    fn caps_visa_at_nineteen() {
        // 25 digits in -> Visa keeps its 19-digit maximum.
        let formatted = format_card_number("4111111111111111111111111");
        assert_eq!(formatted, "4111 1111 1111 1111 111");
        assert_eq!(digit_count(&formatted), 19);
    }

    #[test]
    fn caps_amex_at_fifteen() {
        // 20 digits in, Amex prefix -> capped to 15 and grouped 4-6-5.
        let formatted = format_card_number("37828224631000512345");
        assert_eq!(formatted, "3782 822463 10005");
        assert_eq!(digit_count(&formatted), 15);
    }

    #[test]
    fn caps_mastercard_at_sixteen() {
        let formatted = format_card_number("5500000000000004999");
        assert_eq!(digit_count(&formatted), 16);
    }

    #[test]
    fn unknown_network_caps_at_global_max() {
        // Unrecognised prefix -> falls back to MAX_PAN_DIGITS.
        let formatted = format_card_number("9999999999999999999999");
        assert_eq!(digit_count(&formatted), MAX_PAN_DIGITS);
    }

    #[test]
    fn card_digits_strips_and_caps() {
        // Separators removed, no grouping, capped to the network maximum.
        assert_eq!(card_digits("4111 1111-1111_1111"), "4111111111111111");
        assert_eq!(card_digits("37828224631000512345"), "378282246310005"); // Amex -> 15
        assert_eq!(card_digits(""), "");
    }

    #[test]
    fn space_indices_match_groups() {
        assert_eq!(card_space_indices("4111111111111111"), vec![4, 8, 12]);
        assert_eq!(card_space_indices("378282246310005"), vec![4, 10]); // Amex 4-6-5
        assert_eq!(card_space_indices("41111"), vec![4]); // partial: "4111 1"
        assert_eq!(card_space_indices("411"), Vec::<usize>::new()); // single group
        assert_eq!(card_space_indices(""), Vec::<usize>::new());
    }

    #[test]
    fn network_max_lengths() {
        assert_eq!(CardNetwork::Amex.max_len(), 15);
        assert_eq!(CardNetwork::Mastercard.max_len(), 16);
        assert_eq!(CardNetwork::Visa.max_len(), 19);
        assert_eq!(CardNetwork::Unknown.max_len(), MAX_PAN_DIGITS);
    }

    fn digit_count(s: &str) -> usize {
        s.chars().filter(char::is_ascii_digit).count()
    }

    #[test]
    fn luhn_checks() {
        assert!(luhn_valid("4111111111111111"));
        assert!(luhn_valid("378282246310005"));
        assert!(luhn_valid("6011000990139424"));
        assert!(!luhn_valid("4111111111111112"));
        assert!(!luhn_valid(""));
    }

    #[test]
    fn luhn_ignores_separators() {
        assert!(luhn_valid("4111 1111 1111 1111"));
    }

    #[test]
    fn cvv_lengths() {
        assert_eq!(CardNetwork::Amex.cvv_len(), 4);
        assert_eq!(CardNetwork::Visa.cvv_len(), 3);
        assert_eq!(CardNetwork::Mastercard.cvv_len(), 3);
    }

    #[test]
    fn full_validation() {
        assert!(Card::parse("4111 1111 1111 1111").is_valid());
        assert!(Card::parse("378282246310005").is_valid());
        // Visa prefix but wrong length -> not valid.
        assert!(!Card::parse("411111111111").is_valid());
        // Right length, but Luhn fails.
        assert!(!Card::parse("4111111111111112").is_valid());
    }
}
