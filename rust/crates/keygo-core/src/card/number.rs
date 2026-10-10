//! The primary account number (PAN): parse "dirty" input into a cleaned, capped,
//! network-grouped [`Card`], and validate it.

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
    /// Parse a (possibly dirty) number: strip non-digits, detect the network,
    /// cap to that network's maximum length, and group for display.
    pub fn parse(input: &str) -> Card {
        let cleaned = digits_only(input);
        let network = CardNetwork::from_digits(&cleaned);
        // Capping never changes detection: the IIN lives in the first six
        // digits, well within every network's maximum.
        let digits: String = cleaned.chars().take(network.max_len()).collect();
        let formatted = group(&digits, &network.groups(digits.len()));
        Card {
            network,
            digits,
            formatted,
        }
    }

    /// Digit offsets at which a grouping space falls, e.g. `[4, 8, 12]` for a
    /// 16-digit number.
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

    /// Structurally valid: a length the (possibly [`Unknown`](CardNetwork::Unknown)) network
    /// accepts and a passing Luhn check. The network is deliberately *not* gated on being
    /// recognised: an unrecognised IIN is still acceptable as long as its length is plausible
    /// and the checksum holds.
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
        // Prefix already identifies Amex, so partial input uses 4-6-5, not fours.
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
        // 25 digits in -> Visa keeps its 19-digit maximum.
        let card = Card::parse("4111111111111111111111111");
        assert_eq!(card.formatted, "4111 1111 1111 1111 111");
        assert_eq!(card.digits.len(), 19);
    }

    #[test]
    fn caps_amex_at_fifteen() {
        // 20 digits in, Amex prefix -> capped to 15 and grouped 4-6-5.
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
        // Unrecognised prefix -> falls back to MAX_PAN_DIGITS.
        assert_eq!(
            Card::parse("9999999999999999999999").digits.len(),
            MAX_PAN_DIGITS
        );
    }

    #[test]
    fn card_digits_strips_and_caps() {
        // Separators removed, no grouping, capped to the network maximum.
        assert_eq!(
            Card::parse("4111 1111-1111_1111").digits,
            "4111111111111111"
        );
        assert_eq!(
            Card::parse("37828224631000512345").digits,
            "378282246310005"
        ); // Amex -> 15
        assert_eq!(Card::parse("").digits, "");
    }

    #[test]
    fn space_indices_match_groups() {
        assert_eq!(
            Card::parse("4111111111111111").space_indices(),
            vec![4, 8, 12]
        );
        assert_eq!(Card::parse("378282246310005").space_indices(), vec![4, 10]); // Amex 4-6-5
        assert_eq!(Card::parse("41111").space_indices(), vec![4]); // partial: "4111 1"
        assert_eq!(Card::parse("411").space_indices(), Vec::<usize>::new()); // single group
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
        // Visa prefix but wrong length -> not valid.
        assert!(!Card::parse("411111111111").is_valid());
        // Right length, but Luhn fails.
        assert!(!Card::parse("4111111111111112").is_valid());
    }

    #[test]
    fn unknown_network_validates_on_length_and_luhn() {
        // 9-prefix is an unrecognised network, but a plausible-length, Luhn-valid number is
        // still accepted: validity is not gated on recognising the network.
        let card = Card::parse("9999999999999995");
        assert_eq!(card.network, crate::card::CardNetwork::Unknown);
        assert!(card.is_valid());
        // 11 digits is shorter than any plausible PAN -> rejected on length alone.
        assert!(!Card::parse("99999999995").is_valid());
    }
}
