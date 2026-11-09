use keygo_core::UserId;

/// What OPAQUE binds the password file to: registration, login and step-up must all agree on it.
pub const fn opaque_identifier(user_id: &UserId) -> &[u8] {
    user_id.0.as_bytes()
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    #[test]
    fn opaque_identifier_is_pinned() {
        let bytes = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15];
        let user_id = UserId::from(Uuid::from_bytes(bytes));

        assert_eq!(opaque_identifier(&user_id), bytes);
    }

    #[test]
    fn user_id_travels_as_the_uuids_string_form() {
        let text = "00010203-0405-0607-0809-0a0b0c0d0e0f";
        let user_id = UserId::from(Uuid::from_bytes([
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        ]));

        assert_eq!(serde_json::to_value(user_id).unwrap(), text);
        assert_eq!(
            serde_json::from_value::<UserId>(text.into()).unwrap(),
            user_id
        );
    }
}
