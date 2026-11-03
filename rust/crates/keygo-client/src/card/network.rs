use super::{MAX_PAN_DIGITS, digits_only};

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
        Self::from_digits(&digits_only(number))
    }

    pub(super) fn from_digits(digits: &str) -> CardNetwork {
        use CardNetwork::*;
        if digits.is_empty() {
            return Unknown;
        }

        // American Express: 34, 37
        if matches!(prefix(digits, 2), Some(34 | 37)) {
            return Amex;
        }
        // JCB: 3528-3589, before Diners
        if matches!(prefix(digits, 4), Some(3528..=3589)) {
            return Jcb;
        }
        // Diners Club: 300-305, 36, 38, 39
        if matches!(prefix(digits, 3), Some(300..=305))
            || matches!(prefix(digits, 2), Some(36 | 38 | 39))
        {
            return DinersClub;
        }
        // Visa: 4
        if digits.starts_with('4') {
            return Visa;
        }
        // Mastercard: 51-55, 2221-2720
        if matches!(prefix(digits, 2), Some(51..=55))
            || matches!(prefix(digits, 4), Some(2221..=2720))
        {
            return Mastercard;
        }
        // Discover: 6011, 644-649, 65, 622126-622925 (before UnionPay)
        if matches!(prefix(digits, 4), Some(6011))
            || matches!(prefix(digits, 3), Some(644..=649))
            || matches!(prefix(digits, 2), Some(65))
            || matches!(prefix(digits, 6), Some(622126..=622925))
        {
            return Discover;
        }
        // UnionPay: 62, 81
        if matches!(prefix(digits, 2), Some(62 | 81)) {
            return UnionPay;
        }
        // Maestro: common debit ranges
        if matches!(prefix(digits, 2), Some(50 | 56 | 57 | 58 | 63 | 67 | 69)) {
            return Maestro;
        }

        Unknown
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
            // Unknown IIN: any plausible length, so Luhn can still validate it.
            CardNetwork::Unknown => &[12, 13, 14, 15, 16, 17, 18, 19],
        }
    }

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

    pub(super) fn groups(self, len: usize) -> Vec<usize> {
        match self {
            CardNetwork::Amex => fit_pattern(&[4, 6, 5], len),
            CardNetwork::DinersClub => fit_pattern(&[4, 6, 4], len),
            _ => groups_of_four(len),
        }
    }
}

fn prefix(digits: &str, n: usize) -> Option<u32> {
    digits.get(..n).and_then(|p| p.parse().ok())
}

/// Cuts `pattern` to `len` digits so partial input groups like the full number.
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

#[cfg(test)]
mod tests {
    use super::CardNetwork;
    use crate::card::MAX_PAN_DIGITS;

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
        assert_eq!(CardNetwork::detect("3530111333300000"), CardNetwork::Jcb);
    }

    #[test]
    fn network_max_lengths() {
        assert_eq!(CardNetwork::Amex.max_len(), 15);
        assert_eq!(CardNetwork::Mastercard.max_len(), 16);
        assert_eq!(CardNetwork::Visa.max_len(), 19);
        assert_eq!(CardNetwork::Unknown.max_len(), MAX_PAN_DIGITS);
    }

    #[test]
    fn cvv_lengths() {
        assert_eq!(CardNetwork::Amex.cvv_len(), 4);
        assert_eq!(CardNetwork::Visa.cvv_len(), 3);
        assert_eq!(CardNetwork::Mastercard.cvv_len(), 3);
    }
}
