use std::sync::Arc;

use lib::card::{
    card_digits as core_card_digits, card_space_indices as core_card_space_indices,
    format_card_number as core_format_card_number, luhn_valid as core_luhn_valid,
};

#[derive(uniffi::Object)]
pub struct CardFormatter;

#[uniffi::export]
impl CardFormatter {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn digits(&self, input: String) -> String {
        core_card_digits(&input)
    }

    pub fn format_number(&self, input: String) -> String {
        core_format_card_number(&input)
    }

    pub fn space_indices(&self, input: String) -> Vec<i32> {
        core_card_space_indices(&input)
            .into_iter()
            .map(|i| i as i32)
            .collect()
    }

    pub fn is_luhn_valid(&self, input: String) -> bool {
        core_luhn_valid(&input)
    }
}
