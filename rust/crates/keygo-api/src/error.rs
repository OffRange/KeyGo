use serde::{Deserialize, Serialize};

/// Every `code` the API answers with; clients match on the serialized name.
///
/// A code this build does not know decodes as [`ErrorCode::Unknown`], so a
/// newer server never breaks an older client.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ErrorCode {
    // general
    Internal,
    InvalidRequest,
    NotFound,
    RateLimited,
    // auth
    InvalidUsername,
    InvalidCredentials,
    UsernameTaken,
    UserIdTaken,
    InviteRequired,
    InviteInvalid,
    RegistrationExpired,
    LoginExpired,
    // session
    FreshAuthRequired,
    InvalidToken,
    RefreshExpired,
    SessionRevoked,
    // dpop
    InvalidDpopProof,
    UseDpopNonce,
    #[serde(other)]
    Unknown,
}

/// The JSON body of every error response.
#[derive(Debug, Serialize, Deserialize)]
pub struct ApiError {
    pub code: ErrorCode,
    pub message: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn every_code_keeps_its_wire_name() {
        for (code, name) in [
            (ErrorCode::Internal, "internal"),
            (ErrorCode::InvalidRequest, "invalid_request"),
            (ErrorCode::NotFound, "not_found"),
            (ErrorCode::RateLimited, "rate_limited"),
            (ErrorCode::InvalidUsername, "invalid_username"),
            (ErrorCode::InvalidCredentials, "invalid_credentials"),
            (ErrorCode::UsernameTaken, "username_taken"),
            (ErrorCode::UserIdTaken, "user_id_taken"),
            (ErrorCode::InviteRequired, "invite_required"),
            (ErrorCode::InviteInvalid, "invite_invalid"),
            (ErrorCode::RegistrationExpired, "registration_expired"),
            (ErrorCode::LoginExpired, "login_expired"),
            (ErrorCode::FreshAuthRequired, "fresh_auth_required"),
            (ErrorCode::InvalidToken, "invalid_token"),
            (ErrorCode::RefreshExpired, "refresh_expired"),
            (ErrorCode::SessionRevoked, "session_revoked"),
            (ErrorCode::InvalidDpopProof, "invalid_dpop_proof"),
            (ErrorCode::UseDpopNonce, "use_dpop_nonce"),
        ] {
            assert_eq!(serde_json::to_value(code).unwrap(), name);
            assert_eq!(
                serde_json::from_value::<ErrorCode>(name.into()).unwrap(),
                code
            );
        }
    }

    #[test]
    fn unknown_code_decodes_as_unknown() {
        assert_eq!(
            serde_json::from_value::<ErrorCode>("brand_new".into()).unwrap(),
            ErrorCode::Unknown,
        );
    }

    #[test]
    fn absent_reason_is_omitted() {
        let error = ApiError {
            code: ErrorCode::NotFound,
            message: "m".into(),
            reason: None,
        };
        assert_eq!(
            serde_json::to_value(&error).unwrap(),
            json!({"code": "not_found", "message": "m"})
        );
    }

    #[test]
    fn present_reason_is_written() {
        let error = ApiError {
            code: ErrorCode::SessionRevoked,
            message: "m".into(),
            reason: Some("logout".into()),
        };
        assert_eq!(
            serde_json::to_value(&error).unwrap(),
            json!({"code": "session_revoked", "message": "m", "reason": "logout"}),
        );
    }
}
