use std::fmt;

use super::digits_only;
use super::network::CardNetwork;

#[derive(Debug, Clone)]
pub struct Card {
    pub network: CardNetwork,
    pub digits: String,
    pub formatted: String,
}

impl Card {
    pub fn parse(input: &str) -> Card {
        let cleaned = digits_only(input);
        let network = CardNetwork::from_digits(&cleaned);
        // The IIN is in the first six digits, so capping never changes detection.
        let digits: String = cleaned.chars().take(network.max_len()).collect();
        let formatted = group(&digits, &network.groups(digits.len()));
        Card {
            network,
            digits,
            formatted,
        }
    }

    /// Offsets of grouping spaces, e.g. `[4, 8, 12]`.
    pub fn space_indices(&self) -> Vec<usize> {
        let mut indices = Vec::new();
        let mut pos = 0;
        for size in self.network.groups(self.digits.len()) {
            pos += size;
            if pos < self.digits.len() {
                indices.push(pos);
            }
        }
        indices
    }

    pub fn cvv_len(&self) -> usize {
        self.network.cvv_len()
    }

    pub fn is_length_valid(&self) -> bool {
        self.network.valid_lengths().contains(&self.digits.len())
    }

    pub fn is_luhn_valid(&self) -> bool {
        if self.digits.is_empty() {
            return false;
        }
        let sum: u32 = self
            .digits
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

    /// Valid length and Luhn; the network need not be recognised.
    pub fn is_valid(&self) -> bool {
        self.is_length_valid() && self.is_luhn_valid()
    }
}

impl fmt::Display for Card {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.formatted)
    }
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
    use super::Card;
    use crate::card::MAX_PAN_DIGITS;

    #[test]
    fn formats_by_network() {
        assert_eq!(
            Card::parse("4111111111111111").formatted,
            "4111 1111 1111 1111"
        );
        assert_eq!(
            Card::parse("378282246310005").formatted,
            "3782 822463 10005"
        );
        assert_eq!(Card::parse("30569309025904").formatted, "3056 930902 5904");
    }

    #[test]
    fn ignores_existing_separators() {
        assert_eq!(
            Card::parse("5500-0000 0000_0004").formatted,
            "5500 0000 0000 0004"
        );
    }

    #[test]
    fn formats_partial_input_as_typed() {
        assert_eq!(Card::parse("41111").formatted, "4111 1");
        assert_eq!(Card::parse("411111111").formatted, "4111 1111 1");
    }

    #[test]
    fn amex_groups_progressively_while_typing() {
        assert_eq!(Card::parse("341234").formatted, "3412 34");
        assert_eq!(Card::parse("3412345678").formatted, "3412 345678");
        assert_eq!(Card::parse("34123456789012").formatted, "3412 345678 9012");
        assert_eq!(
            Card::parse("341234567890123").formatted,
            "3412 345678 90123"
        );
        assert_eq!(Card::parse("3412345678").space_indices(), vec![4]);
        assert_eq!(Card::parse("34123456789012").space_indices(), vec![4, 10]);
    }

    #[test]
    fn nineteen_digits_trail_in_fours() {
        assert_eq!(
            Card::parse("4111111111111111123").formatted,
            "4111 1111 1111 1111 123"
        );
    }

    #[test]
    fn caps_visa_at_nineteen() {
        let card = Card::parse("4111111111111111111111111");
        assert_eq!(card.formatted, "4111 1111 1111 1111 111");
        assert_eq!(card.digits.len(), 19);
    }

    #[test]
    fn caps_amex_at_fifteen() {
        let card = Card::parse("37828224631000512345");
        assert_eq!(card.formatted, "3782 822463 10005");
        assert_eq!(card.digits.len(), 15);
    }

    #[test]
    fn caps_mastercard_at_sixteen() {
        assert_eq!(Card::parse("5500000000000004999").digits.len(), 16);
    }

    #[test]
    fn unknown_network_caps_at_global_max() {
        assert_eq!(
            Card::parse("9999999999999999999999").digits.len(),
            MAX_PAN_DIGITS
        );
    }

    #[test]
    fn card_digits_strips_and_caps() {
        assert_eq!(
            Card::parse("4111 1111-1111_1111").digits,
            "4111111111111111"
        );
        assert_eq!(
            Card::parse("37828224631000512345").digits,
            "378282246310005"
        );
        assert_eq!(Card::parse("").digits, "");
    }

    #[test]
    fn space_indices_match_groups() {
        assert_eq!(
            Card::parse("4111111111111111").space_indices(),
            vec![4, 8, 12]
        );
        assert_eq!(Card::parse("378282246310005").space_indices(), vec![4, 10]);
        assert_eq!(Card::parse("41111").space_indices(), vec![4]);
        assert_eq!(Card::parse("411").space_indices(), Vec::<usize>::new());
        assert_eq!(Card::parse("").space_indices(), Vec::<usize>::new());
    }

    #[test]
    fn luhn_checks() {
        assert!(Card::parse("4111111111111111").is_luhn_valid());
        assert!(Card::parse("378282246310005").is_luhn_valid());
        assert!(Card::parse("6011000990139424").is_luhn_valid());
        assert!(!Card::parse("4111111111111112").is_luhn_valid());
        assert!(!Card::parse("").is_luhn_valid());
    }

    #[test]
    fn luhn_ignores_separators() {
        assert!(Card::parse("4111 1111 1111 1111").is_luhn_valid());
    }

    #[test]
    fn cvv_len_surfaces_network_value() {
        assert_eq!(Card::parse("378282246310005").cvv_len(), 4);
        assert_eq!(Card::parse("4111111111111111").cvv_len(), 3);
    }

    #[test]
    fn full_validation() {
        assert!(Card::parse("4111 1111 1111 1111").is_valid());
        assert!(Card::parse("378282246310005").is_valid());
        assert!(!Card::parse("411111111111").is_valid());
        assert!(!Card::parse("4111111111111112").is_valid());
    }

    #[test]
    fn unknown_network_validates_on_length_and_luhn() {
        let card = Card::parse("9999999999999995");
        assert_eq!(card.network, crate::card::CardNetwork::Unknown);
        assert!(card.is_valid());
        assert!(!Card::parse("99999999995").is_valid());
    }
}
