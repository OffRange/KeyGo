use crate::domain::SessionId;
use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use ed25519_dalek::{Signature, Signer, SigningKey};
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use thiserror::Error;
use uuid::Uuid;
use zeroize::{Zeroize, ZeroizeOnDrop};

pub const ACCESS_TOKEN_LIFETIME: Duration = Duration::from_mins(10);
pub const FRESH_AUTH_WINDOW: Duration = Duration::from_mins(5);

const JTI_BYTES: usize = 32;

pub struct AccessTokenKey {
    signing: SigningKey,
    kid: String,
}

pub struct TokenIssuer {
    key: AccessTokenKey,
    iss: String,
}

impl TokenIssuer {
    pub fn new(key: AccessTokenKey, iss: impl Into<String>) -> Self {
        Self {
            key,
            iss: iss.into(),
        }
    }

    pub fn issue(
        &self,
        user_id: Uuid,
        session_id: SessionId,
        auth_time: u64,
        jkt: &[u8; 32],
    ) -> AccessToken {
        self.key.sign(&Claims::new(
            self.iss.clone(),
            user_id,
            session_id,
            auth_time,
            jkt,
        ))
    }

    pub fn verify(&self, token: &str) -> Result<Claims, TokenError> {
        self.key.verify(token, &self.iss)
    }
}

#[derive(Serialize, Deserialize)]
struct Cnf {
    #[serde(with = "base64url")]
    jkt: [u8; 32],
}

#[derive(Serialize, Deserialize)]
enum Aud {
    #[serde(rename = "keygo-api")]
    KeyGoApi,
}

// TODO: add `pwe` (password_epoch) once the epoch exists.
#[derive(Serialize, Deserialize)]
pub struct Claims {
    iss: String,
    aud: Aud,
    #[serde(rename = "sub")]
    user_id: Uuid,
    #[serde(rename = "sid")]
    session_id: SessionId,
    iat: u64,
    exp: u64,
    auth_time: u64,
    cnf: Cnf,
    #[serde(with = "base64url")]
    jti: [u8; JTI_BYTES],
}

#[derive(Serialize, Deserialize)]
enum Alg {
    EdDSA,
}

#[derive(Serialize, Deserialize)]
enum Typ {
    #[serde(rename = "at+jwt")]
    AtJwt,
}

#[derive(Serialize, Deserialize)]
struct Header {
    alg: Alg,
    typ: Typ,
    kid: String,
}

#[derive(Serialize, Zeroize, ZeroizeOnDrop)]
pub struct AccessToken(String);

#[derive(Debug, Clone, Copy, PartialEq, Eq, Error)]
pub enum TokenError {
    #[error("token is not a well-formed access token")]
    Malformed,
    #[error("token was signed by an unknown key")]
    UnknownKey,
    #[error("token signature does not verify")]
    InvalidSignature,
    #[error("token was issued by another server")]
    WrongIssuer,
    #[error("token has expired")]
    Expired,
}

impl Claims {
    fn new(
        iss: String,
        user_id: Uuid,
        session_id: SessionId,
        auth_time: u64,
        jkt: &[u8; 32],
    ) -> Self {
        Self::new_at(iss, user_id, session_id, auth_time, jkt, unix_now())
    }

    fn new_at(
        iss: String,
        user_id: Uuid,
        session_id: SessionId,
        auth_time: u64,
        jkt: &[u8; 32],
        now: u64,
    ) -> Self {
        let mut jti = [0u8; JTI_BYTES];
        getrandom::fill(jti.as_mut_slice()).expect("OS random number generator unavailable");

        Self {
            iss,
            aud: Aud::KeyGoApi,
            user_id,
            session_id,
            iat: now,
            exp: now + ACCESS_TOKEN_LIFETIME.as_secs(),
            auth_time,
            cnf: Cnf { jkt: *jkt },
            jti,
        }
    }

    pub fn user_id(&self) -> Uuid {
        self.user_id
    }

    pub fn session_id(&self) -> SessionId {
        self.session_id
    }

    pub fn auth_time(&self) -> u64 {
        self.auth_time
    }

    pub fn jkt(&self) -> &[u8; 32] {
        &self.cnf.jkt
    }

    pub fn is_fresh(&self) -> bool {
        self.is_fresh_at(unix_now())
    }

    fn is_fresh_at(&self, now: u64) -> bool {
        now.saturating_sub(self.auth_time) <= FRESH_AUTH_WINDOW.as_secs()
    }
}

impl AccessToken {
    #[cfg(test)]
    pub(crate) fn expose(&self) -> &str {
        &self.0
    }
}

impl AccessTokenKey {
    pub fn new(seed: &[u8; 32]) -> Self {
        let signing = SigningKey::from_bytes(seed);
        let digest = Sha256::digest(signing.verifying_key().as_bytes());
        let kid = hex::encode(&digest[..8]);

        Self { signing, kid }
    }

    fn sign(&self, claims: &Claims) -> AccessToken {
        let header = Header {
            alg: Alg::EdDSA,
            typ: Typ::AtJwt,
            kid: self.kid.clone(),
        };

        let input = format!("{}.{}", encode(&header), encode(claims));
        let signature = self.signing.sign(input.as_bytes());

        AccessToken(format!(
            "{input}.{}",
            URL_SAFE_NO_PAD.encode(signature.to_bytes())
        ))
    }

    fn verify(&self, token: &str, iss: &str) -> Result<Claims, TokenError> {
        self.verify_at(token, iss, unix_now())
    }

    fn verify_at(&self, token: &str, iss: &str, now: u64) -> Result<Claims, TokenError> {
        let (input, signature) = token.rsplit_once('.').ok_or(TokenError::Malformed)?;
        let (header, claims) = input.split_once('.').ok_or(TokenError::Malformed)?;

        let header: Header = decode(header)?;
        if header.kid != self.kid {
            return Err(TokenError::UnknownKey);
        }

        let signature = URL_SAFE_NO_PAD
            .decode(signature)
            .ok()
            .and_then(|bytes| Signature::from_slice(&bytes).ok())
            .ok_or(TokenError::Malformed)?;
        self.signing
            .verifying_key()
            .verify_strict(input.as_bytes(), &signature)
            .map_err(|_| TokenError::InvalidSignature)?;

        let claims: Claims = decode(claims)?;
        if claims.iss != iss {
            return Err(TokenError::WrongIssuer);
        }
        if now >= claims.exp {
            return Err(TokenError::Expired);
        }
        Ok(claims)
    }
}

pub(crate) fn unix_now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("system clock is set before the Unix epoch")
        .as_secs()
}

fn encode<T: Serialize>(value: &T) -> String {
    URL_SAFE_NO_PAD.encode(serde_json::to_vec(value).expect("types always serialize"))
}

fn decode<T: DeserializeOwned>(segment: &str) -> Result<T, TokenError> {
    let json = URL_SAFE_NO_PAD
        .decode(segment)
        .map_err(|_| TokenError::Malformed)?;
    serde_json::from_slice(&json).map_err(|_| TokenError::Malformed)
}

mod base64url {
    use base64::Engine;
    use base64::engine::general_purpose::URL_SAFE_NO_PAD;
    use serde::de::Error;
    use serde::{Deserialize, Deserializer, Serializer};

    pub fn serialize<S: Serializer, const N: usize>(
        bytes: &[u8; N],
        serializer: S,
    ) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(&URL_SAFE_NO_PAD.encode(bytes))
    }

    pub fn deserialize<'de, D: Deserializer<'de>, const N: usize>(
        deserializer: D,
    ) -> Result<[u8; N], D::Error> {
        let encoded = <&str>::deserialize(deserializer)?;
        URL_SAFE_NO_PAD
            .decode(encoded)
            .ok()
            .and_then(|bytes| bytes.try_into().ok())
            .ok_or_else(|| D::Error::custom(format!("expected {N} bytes of base64url")))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use ed25519_dalek::Verifier;
    use serde_json::{Value, json};

    const SEED: [u8; 32] = [0x07; 32];
    const JKT: [u8; 32] = [0x41; 32];
    const ISS: &str = "https://keygo.example";
    const NOW: u64 = 1_791_504_000;

    fn fixed_claims() -> Claims {
        Claims {
            jti: [0x42; JTI_BYTES],
            ..Claims::new_at(
                ISS.into(),
                Uuid::from_u128(1),
                Uuid::from_u128(2),
                1_791_503_940,
                &JKT,
                NOW,
            )
        }
    }

    fn part(token: &str, index: usize) -> Value {
        let segment = token.split('.').nth(index).unwrap();
        serde_json::from_slice(&URL_SAFE_NO_PAD.decode(segment).unwrap()).unwrap()
    }

    fn with_part(token: &str, index: usize, value: Value) -> String {
        let mut parts: Vec<String> = token.split('.').map(String::from).collect();
        parts[index] = URL_SAFE_NO_PAD.encode(serde_json::to_vec(&value).unwrap());
        parts.join(".")
    }

    #[test]
    fn signs_pinned_token() {
        let token = AccessTokenKey::new(&SEED).sign(&fixed_claims());

        assert_eq!(
            token.expose(),
            concat!(
                "eyJhbGciOiJFZERTQSIsInR5cCI6ImF0K2p3dCIsImtpZCI6ImZlODEyYzEyZjNhYjRjZTYifQ.",
                "eyJpc3MiOiJodHRwczovL2tleWdvLmV4YW1wbGUiLCJhdWQiOiJrZXlnby1hcGkiLCJzdWIiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDEiLCJzaWQiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDIiLCJpYXQiOjE3OTE1MDQwMDAsImV4cCI6MTc5MTUwNDYwMCwiYXV0aF90aW1lIjoxNzkxNTAzOTQwLCJjbmYiOnsiamt0IjoiUVVGQlFVRkJRVUZCUVVGQlFVRkJRVUZCUVVGQlFVRkJRVUZCUVVGQlFVRSJ9LCJqdGkiOiJRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtJIn0.",
                "3RCy0TvWwahXCYg7tQ4Wq34cgsuJMdI5ocC26HYRT-rQf8ayNnr5sSfgYxBVHdawh39Up55m8K2tgprgiXFHBg",
            ),
        );
    }

    #[test]
    fn header_names_alg_typ_and_kid() {
        let token = AccessTokenKey::new(&SEED).sign(&fixed_claims());

        assert_eq!(
            part(token.expose(), 0),
            json!({ "alg": "EdDSA", "typ": "at+jwt", "kid": "fe812c12f3ab4ce6" }),
        );
    }

    #[test]
    fn claims_use_spec_names_and_string_encodings() {
        let token = AccessTokenKey::new(&SEED).sign(&fixed_claims());

        assert_eq!(
            part(token.expose(), 1),
            json!({
                "iss": "https://keygo.example",
                "aud": "keygo-api",
                "sub": "00000000-0000-0000-0000-000000000001",
                "sid": "00000000-0000-0000-0000-000000000002",
                "iat": 1_791_504_000,
                "exp": 1_791_504_600,
                "auth_time": 1_791_503_940,
                "cnf": { "jkt": "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE" },
                "jti": "QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI",
            }),
        );
    }

    #[test]
    fn signature_verifies_under_the_public_key() {
        let key = AccessTokenKey::new(&SEED);
        let token = key.sign(&fixed_claims());

        let (input, sig) = token.expose().rsplit_once('.').unwrap();
        let sig = Signature::from_slice(&URL_SAFE_NO_PAD.decode(sig).unwrap()).unwrap();

        key.signing
            .verifying_key()
            .verify(input.as_bytes(), &sig)
            .unwrap();
    }

    #[test]
    fn expires_after_lifetime() {
        let claims = Claims::new_at(String::new(), Uuid::nil(), Uuid::nil(), 0, &JKT, 1_000);

        assert_eq!(claims.iat, 1_000);
        assert_eq!(claims.exp, 1_000 + ACCESS_TOKEN_LIFETIME.as_secs());
    }

    #[test]
    fn generated_jtis_differ() {
        let new = || Claims::new_at(String::new(), Uuid::nil(), Uuid::nil(), 0, &JKT, 0).jti;

        assert_ne!(new(), new());
    }

    #[test]
    fn serializes_as_the_token() {
        let token = AccessTokenKey::new(&SEED).sign(&fixed_claims());

        assert_eq!(serde_json::to_value(&token).unwrap(), token.expose());
    }

    #[test]
    fn verify_returns_the_signed_claims() {
        let key = AccessTokenKey::new(&SEED);
        let token = key.sign(&fixed_claims());

        let claims = key.verify_at(token.expose(), ISS, NOW).unwrap();

        assert_eq!(claims.user_id(), Uuid::from_u128(1));
        assert_eq!(claims.session_id(), Uuid::from_u128(2));
        assert_eq!(claims.auth_time(), 1_791_503_940);
        assert_eq!(claims.jkt(), &JKT);
    }

    #[test]
    fn verify_rejects_tampered_claims() {
        let key = AccessTokenKey::new(&SEED);
        let token = key.sign(&fixed_claims());
        let mut claims = part(token.expose(), 1);
        claims["sub"] = json!("00000000-0000-0000-0000-000000000009");

        let tampered = with_part(token.expose(), 1, claims);

        assert_eq!(
            key.verify_at(&tampered, ISS, NOW).err(),
            Some(TokenError::InvalidSignature),
        );
    }

    #[test]
    fn verify_rejects_another_key() {
        let token = AccessTokenKey::new(&[0x08; 32]).sign(&fixed_claims());

        assert_eq!(
            AccessTokenKey::new(&SEED)
                .verify_at(token.expose(), ISS, NOW)
                .err(),
            Some(TokenError::UnknownKey),
        );
    }

    #[test]
    fn verify_rejects_a_foreign_signature_under_our_kid() {
        let key = AccessTokenKey::new(&SEED);
        let ours = key.sign(&fixed_claims());
        let theirs = AccessTokenKey::new(&[0x08; 32]).sign(&fixed_claims());
        let (input, _) = ours.expose().rsplit_once('.').unwrap();
        let (_, signature) = theirs.expose().rsplit_once('.').unwrap();

        assert_eq!(
            key.verify_at(&format!("{input}.{signature}"), ISS, NOW)
                .err(),
            Some(TokenError::InvalidSignature),
        );
    }

    #[test]
    fn verify_rejects_alg_none() {
        let key = AccessTokenKey::new(&SEED);
        let token = key.sign(&fixed_claims());
        let header = json!({ "alg": "none", "typ": "at+jwt", "kid": "fe812c12f3ab4ce6" });

        let unsigned = with_part(token.expose(), 0, header);

        assert_eq!(
            key.verify_at(&unsigned, ISS, NOW).err(),
            Some(TokenError::Malformed),
        );
    }

    #[test]
    fn verify_rejects_another_typ() {
        let key = AccessTokenKey::new(&SEED);
        let token = key.sign(&fixed_claims());
        let header = json!({ "alg": "EdDSA", "typ": "dpop+jwt", "kid": "fe812c12f3ab4ce6" });

        assert_eq!(
            key.verify_at(&with_part(token.expose(), 0, header), ISS, NOW)
                .err(),
            Some(TokenError::Malformed),
        );
    }

    #[test]
    fn verify_rejects_another_issuer() {
        let key = AccessTokenKey::new(&SEED);
        let token = key.sign(&fixed_claims());

        assert_eq!(
            key.verify_at(token.expose(), "https://other.example", NOW)
                .err(),
            Some(TokenError::WrongIssuer),
        );
    }

    #[test]
    fn verify_rejects_another_audience() {
        let key = AccessTokenKey::new(&SEED);
        let mut claims = serde_json::to_value(fixed_claims()).unwrap();
        claims["aud"] = json!("other-api");
        let input = format!(
            "{}.{}",
            part_raw(key.sign(&fixed_claims()).expose(), 0),
            URL_SAFE_NO_PAD.encode(serde_json::to_vec(&claims).unwrap()),
        );
        let signature = key.signing.sign(input.as_bytes());
        let token = format!("{input}.{}", URL_SAFE_NO_PAD.encode(signature.to_bytes()));

        assert_eq!(
            key.verify_at(&token, ISS, NOW).err(),
            Some(TokenError::Malformed)
        );
    }

    #[test]
    fn verify_rejects_from_exp_on() {
        let key = AccessTokenKey::new(&SEED);
        let token = key.sign(&fixed_claims());
        let exp = NOW + ACCESS_TOKEN_LIFETIME.as_secs();

        assert!(key.verify_at(token.expose(), ISS, exp - 1).is_ok());
        assert_eq!(
            key.verify_at(token.expose(), ISS, exp).err(),
            Some(TokenError::Expired),
        );
    }

    #[test]
    fn verify_rejects_malformed_input() {
        let key = AccessTokenKey::new(&SEED);
        let token = key.sign(&fixed_claims());
        let (input, _) = token.expose().rsplit_once('.').unwrap();

        for bad in ["", "a.b", "a.b.c", input, &format!("{input}.!!")] {
            assert_eq!(
                key.verify_at(bad, ISS, NOW).err(),
                Some(TokenError::Malformed),
                "{bad}"
            );
        }
    }

    #[test]
    fn fresh_within_window_after_auth_time() {
        let claims = Claims::new_at(String::new(), Uuid::nil(), Uuid::nil(), 1_000, &JKT, 1_000);

        assert!(claims.is_fresh_at(1_000));
        assert!(claims.is_fresh_at(1_000 + FRESH_AUTH_WINDOW.as_secs()));
        assert!(!claims.is_fresh_at(1_000 + FRESH_AUTH_WINDOW.as_secs() + 1));
    }

    #[test]
    fn token_issued_now_verifies_and_is_fresh() {
        let key = AccessTokenKey::new(&SEED);
        let token = key.sign(&Claims::new(
            ISS.into(),
            Uuid::nil(),
            Uuid::nil(),
            unix_now(),
            &JKT,
        ));

        assert!(key.verify(token.expose(), ISS).unwrap().is_fresh());
    }

    #[test]
    fn issuer_round_trips_its_own_tokens() {
        let issuer = TokenIssuer::new(AccessTokenKey::new(&SEED), ISS);
        let token = issuer.issue(Uuid::from_u128(1), Uuid::from_u128(2), NOW, &JKT);
        let claims = issuer.verify(token.expose()).unwrap();

        assert_eq!(claims.user_id(), Uuid::from_u128(1));
        assert_eq!(claims.session_id(), Uuid::from_u128(2));
        assert_eq!(claims.auth_time(), NOW);
        assert_eq!(claims.jkt(), &JKT);
    }

    #[test]
    fn issuer_rejects_tokens_for_another_issuer() {
        let key = || AccessTokenKey::new(&SEED);
        let token = TokenIssuer::new(key(), "https://other.example").issue(
            Uuid::nil(),
            Uuid::nil(),
            unix_now(),
            &JKT,
        );

        assert_eq!(
            TokenIssuer::new(key(), ISS).verify(token.expose()).err(),
            Some(TokenError::WrongIssuer),
        );
    }

    #[test]
    fn issuer_keeps_the_given_auth_time() {
        let issuer = TokenIssuer::new(AccessTokenKey::new(&SEED), ISS);
        let auth_time = unix_now() - FRESH_AUTH_WINDOW.as_secs() - 1;

        let token = issuer.issue(Uuid::nil(), Uuid::nil(), auth_time, &JKT);
        let claims = issuer.verify(token.expose()).unwrap();

        assert!(!claims.is_fresh());
    }

    fn part_raw(token: &str, index: usize) -> &str {
        token.split('.').nth(index).unwrap()
    }
}
