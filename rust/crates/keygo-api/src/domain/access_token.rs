use serde::{Deserialize, Serialize};
use zeroize::{Zeroize, ZeroizeOnDrop};

#[derive(Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
pub struct AccessToken(String);

impl AccessToken {
    pub fn expose(&self) -> &str {
        &self.0
    }

    #[cfg(feature = "server")]
    pub fn from_signed(token: String) -> Self {
        Self(token)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn round_trips_as_a_plain_string() {
        let token: AccessToken = serde_json::from_value(json!("a.b.c")).unwrap();
        assert_eq!(token.expose(), "a.b.c");
        assert_eq!(serde_json::to_value(&token).unwrap(), "a.b.c");
    }
}
