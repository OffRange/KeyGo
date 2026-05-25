use std::sync::Arc;

use lib::card::{Card, format_expiration_after_edit as core_format_expiration_after_edit};

#[derive(uniffi::Object)]
pub struct CardFormatter;

#[uniffi::export]
impl CardFormatter {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn digits(&self, input: String) -> String {
        Card::parse(&input).digits
    }

    pub fn format_number(&self, input: String) -> String {
        Card::parse(&input).formatted
    }

    pub fn space_indices(&self, input: String) -> Vec<i32> {
        Card::parse(&input)
            .space_indices()
            .into_iter()
            .map(|i| i as i32)
            .collect()
    }

    pub fn is_luhn_valid(&self, input: String) -> bool {
        Card::parse(&input).is_luhn_valid()
    }

    pub fn cvv_len(&self, input: String) -> i32 {
        Card::parse(&input).cvv_len() as i32
    }

    pub fn format_expiration_after_edit(&self, previous: String, proposed: String) -> String {
        core_format_expiration_after_edit(&previous, &proposed)
    }
}
