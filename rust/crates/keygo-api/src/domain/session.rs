use super::string_enum::string_enum;

string_enum! {
    pub enum RevokeReason {
        Replaced => "replaced",
        ReuseDetected => "reuse_detected",
        Logout => "logout",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn revoke_reason_round_trips_its_stored_name() {
        for &reason in RevokeReason::ALL {
            assert_eq!(reason.as_str().parse::<RevokeReason>().unwrap(), reason);
        }
        assert!("logout_typo".parse::<RevokeReason>().is_err());
    }
}
