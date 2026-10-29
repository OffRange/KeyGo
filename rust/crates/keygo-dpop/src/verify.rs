use crate::nonce::{Nonce, NonceKey};
use crate::seen_jtis::{Record, SeenJtis};
use crate::wire::{Claims, Header};
use crate::{Htm, htu, jwk, unix_now, wire};
use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use p256::ecdsa::Signature;
use p256::ecdsa::signature::Verifier;
use std::time::Duration;
use thiserror::Error;

pub struct Expected<'a> {
    pub htm: Htm,
    pub htu: &'a str,
    pub bound: Option<Bound<'a>>,
}

#[derive(Clone, Copy)]
pub struct Bound<'a> {
    pub access_token: &'a str,
    pub jkt: &'a [u8; 32],
}

/// A proof that passed every check that needs no state. A request whose replay could repeat its
/// action must [`record`](Self::record) it before acting.
#[must_use]
pub struct Checked<'a> {
    verifier: &'a DpopVerifier,
    jkt: [u8; 32],
    jti: String,
    deadline: u64,
    now: u64,
}

/// What went wrong, grouped by how the server answers. The 400/401 split is the endpoint's, not the
/// error's: token-issuing endpoints answer 400, everything else 401 with a `WWW-Authenticate: DPoP`
/// challenge.
#[derive(Debug, Error)]
pub enum VerifyError {
    /// Answer `invalid_dpop_proof`. The reason is for logs only; the client reacts to every one
    /// the same way.
    #[error("invalid dpop proof")]
    Invalid(#[from] InvalidProof),
    /// Answer `use_dpop_nonce` and send this nonce in the `DPoP-Nonce` header.
    #[error("dpop nonce required")]
    UseNonce(Nonce),
    /// The replay cache is full. Answer `429 rate_limited`: the proof itself may be fine.
    #[error("too many recent proofs")]
    Busy { retry_after: Duration },
}

#[derive(Debug, Error)]
pub enum InvalidProof {
    #[error("not a compact JWS")]
    Malformed,
    #[error(transparent)]
    Decode(#[from] wire::DecodeError),
    #[error("header lists critical extensions")]
    Critical,
    #[error(transparent)]
    Jwk(#[from] jwk::JwkError),
    #[error("signature does not verify")]
    Signature,
    #[error("htm or htu does not match the request")]
    Request,
    #[error("ath does not match the access token")]
    AccessTokenHash,
    #[error("proof key is not the bound key")]
    KeyMismatch,
    #[error("jti already used")]
    Replay,
}

impl From<wire::DecodeError> for VerifyError {
    fn from(e: wire::DecodeError) -> Self {
        InvalidProof::from(e).into()
    }
}

impl From<jwk::JwkError> for VerifyError {
    fn from(e: jwk::JwkError) -> Self {
        InvalidProof::from(e).into()
    }
}

type VerifyResult<T> = Result<T, VerifyError>;

const IAT_WINDOW: u64 = 60;

pub struct DpopVerifier {
    nonce_key: NonceKey,
    started_at: u64,
    seen_jtis: SeenJtis,
}

impl DpopVerifier {
    #[expect(
        clippy::new_without_default,
        reason = "a verifier owns a random nonce key and a start time, not a neutral default"
    )]
    pub fn new() -> Self {
        Self::started_at(NonceKey::random(), unix_now())
    }

    pub(crate) fn started_at(nonce_key: NonceKey, started_at: u64) -> Self {
        Self {
            nonce_key,
            started_at,
            seen_jtis: SeenJtis::new(),
        }
    }

    pub fn check(&self, proof: &str, expected: Expected) -> VerifyResult<Checked<'_>> {
        self.check_at(proof, expected, unix_now())
    }

    fn check_at(&self, proof: &str, expected: Expected, now: u64) -> VerifyResult<Checked<'_>> {
        let (input, sign) = proof.rsplit_once('.').ok_or(InvalidProof::Malformed)?;
        let (header, claims) = input.split_once('.').ok_or(InvalidProof::Malformed)?;

        // Alg + Typ allowlist is checked during wire::decode, as they are typed enums
        let header = wire::decode::<Header>(header)?;
        if header.crit.is_some() {
            return Err(InvalidProof::Critical.into());
        }
        verify_signature(input, &header, sign)?;

        let Claims {
            jti,
            htm,
            htu,
            iat,
            ath,
            nonce,
        } = wire::decode(claims)?;

        if htm != expected.htm || !htu::matches(&htu, expected.htu) {
            return Err(InvalidProof::Request.into());
        }

        if ath != expected.bound.map(|bound| wire::ath(bound.access_token)) {
            return Err(InvalidProof::AccessTokenHash.into());
        }

        let jkt = header.jwk.thumbprint();
        if expected.bound.is_some_and(|bound| bound.jkt != &jkt) {
            return Err(InvalidProof::KeyMismatch.into());
        }

        let deadline = match nonce {
            Some(nonce) => self
                .nonce_key
                .open(&nonce)
                .filter(|nonce| now <= nonce.expires_at())
                .map(|nonce| nonce.expires_at()),
            None => (now.abs_diff(iat) <= IAT_WINDOW && iat >= self.started_at + IAT_WINDOW)
                .then(|| iat + IAT_WINDOW),
        };
        let Some(deadline) = deadline else {
            return Err(VerifyError::UseNonce(self.nonce_key.issue(now)));
        };

        Ok(Checked {
            verifier: self,
            jkt,
            jti,
            deadline,
            now,
        })
    }
}

impl Checked<'_> {
    /// Records the jti until the proof's deadline; a proof carrying it again is refused.
    pub fn record(self) -> VerifyResult<[u8; 32]> {
        let Self {
            verifier,
            jkt,
            jti,
            deadline,
            now,
        } = self;
        match verifier.seen_jtis.record(&jkt, &jti, deadline, now) {
            Record::New => Ok(jkt),
            Record::Replay => Err(InvalidProof::Replay.into()),
            Record::Full { free_at } => Err(VerifyError::Busy {
                retry_after: Duration::from_secs(free_at.saturating_sub(now)),
            }),
        }
    }

    /// Accepts the proof without storing its jti. Only for a request that consumes single-use
    /// state, so a replayed proof can't do anything twice.
    pub fn skip_replay_check(self) -> [u8; 32] {
        self.jkt
    }
}

fn verify_signature(input: &str, header: &Header, sign: &str) -> Result<(), InvalidProof> {
    let key = header.jwk.verifying_key()?;
    let sign = URL_SAFE_NO_PAD
        .decode(sign)
        .map_err(|_| InvalidProof::Signature)?;
    let sign = Signature::from_slice(&sign).map_err(|_| InvalidProof::Signature)?;
    key.verify(input.as_bytes(), &sign)
        .map_err(|_| InvalidProof::Signature)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testing::TestSigner;
    use crate::{DpopProver, ProofRequest};
    use std::sync::LazyLock;

    const HTU: &str = "https://sync.example/api/v1/items";
    const NOW: u64 = 1_700_000_000;
    const WINDOW: u64 = IAT_WINDOW;
    const NONCE_LIFETIME: u64 = 5 * 60;

    static SIGNER_JKT: LazyLock<[u8; 32]> =
        LazyLock::new(|| *DpopProver::new(TestSigner::new()).jkt());

    fn verifier_started_at(started_at: u64) -> DpopVerifier {
        DpopVerifier::started_at(NonceKey::new([3; 32]), started_at)
    }

    /// Verifies at `NOW` against a server that has been up long enough to accept nonce-less proofs.
    fn verify(proof: &str, expected: Expected) -> VerifyResult<[u8; 32]> {
        verifier_started_at(NOW - WINDOW)
            .check_at(proof, expected, NOW)
            .and_then(Checked::record)
    }

    /// Checks and records a proof carrying no access token on `verifier`, at `now`.
    fn record_at(verifier: &DpopVerifier, proof: &str, now: u64) -> VerifyResult<[u8; 32]> {
        verifier
            .check_at(proof, expected(None), now)
            .and_then(Checked::record)
    }

    fn proof(iat: u64, access_token: Option<&str>) -> String {
        proof_with_nonce(iat, access_token, None)
    }

    fn proof_with_nonce(iat: u64, access_token: Option<&str>, nonce: Option<&str>) -> String {
        let req = ProofRequest {
            htm: Htm::Post,
            htu: HTU,
            access_token,
            nonce,
        };
        DpopProver::new(TestSigner::new())
            .proof_at(&req, iat, "jti")
            .unwrap()
    }

    fn required_nonce(result: VerifyResult<Checked<'_>>) -> Nonce {
        match result {
            Err(VerifyError::UseNonce(nonce)) => nonce,
            Err(e) => panic!("expected a nonce challenge, got {e:?}"),
            Ok(_) => panic!("expected a nonce challenge, got a verified proof"),
        }
    }

    fn expected(access_token: Option<&str>) -> Expected<'_> {
        Expected {
            htm: Htm::Post,
            htu: HTU,
            bound: access_token.map(|access_token| Bound {
                access_token,
                jkt: &SIGNER_JKT,
            }),
        }
    }

    /// Swaps one segment of a compact JWS, leaving the others untouched.
    fn replace_segment(proof: &str, index: usize, segment: &str) -> String {
        let mut parts: Vec<&str> = proof.split('.').collect();
        parts[index] = segment;
        parts.join(".")
    }

    #[test]
    fn accepts_own_proof() {
        let jkt = verify(&proof(NOW, None), expected(None)).unwrap();

        assert_eq!(&jkt, DpopProver::new(TestSigner::new()).jkt());
    }

    #[test]
    fn accepts_matching_access_token() {
        assert!(verify(&proof(NOW, Some("token")), expected(Some("token"))).is_ok());
    }

    #[test]
    fn rejects_wrong_access_token() {
        let result = verify(&proof(NOW, Some("token")), expected(Some("other")));

        assert!(matches!(
            result,
            Err(VerifyError::Invalid(InvalidProof::AccessTokenHash))
        ));
    }

    #[test]
    fn rejects_missing_ath_when_token_present() {
        let result = verify(&proof(NOW, None), expected(Some("token")));

        assert!(matches!(
            result,
            Err(VerifyError::Invalid(InvalidProof::AccessTokenHash))
        ));
    }

    #[test]
    fn rejects_unexpected_ath() {
        let result = verify(&proof(NOW, Some("token")), expected(None));

        assert!(matches!(
            result,
            Err(VerifyError::Invalid(InvalidProof::AccessTokenHash))
        ));
    }

    #[test]
    fn rejects_other_method_or_uri() {
        let mut wrong_htm = expected(None);
        wrong_htm.htm = Htm::Get;
        let mut wrong_htu = expected(None);
        wrong_htu.htu = "https://sync.example/api/v1/other";

        assert!(matches!(
            verify(&proof(NOW, None), wrong_htm),
            Err(VerifyError::Invalid(InvalidProof::Request))
        ));
        assert!(matches!(
            verify(&proof(NOW, None), wrong_htu),
            Err(VerifyError::Invalid(InvalidProof::Request))
        ));
    }

    #[test]
    fn rejects_tampered_claims() {
        let other = proof(NOW + 1, None);
        let claims = other.split('.').nth(1).unwrap();
        let tampered = replace_segment(&proof(NOW, None), 1, claims);

        assert!(matches!(
            verify(&tampered, expected(None)),
            Err(VerifyError::Invalid(InvalidProof::Signature))
        ));
    }

    #[test]
    fn rejects_proof_signed_by_another_key() {
        let foreign = DpopProver::new(TestSigner::from_seed(9))
            .proof_at(
                &ProofRequest {
                    htm: Htm::Post,
                    htu: HTU,
                    access_token: None,
                    nonce: None,
                },
                NOW,
                "jti",
            )
            .unwrap();
        let signature = foreign.rsplit('.').next().unwrap();
        let forged = replace_segment(&proof(NOW, None), 2, signature);

        assert!(matches!(
            verify(&forged, expected(None)),
            Err(VerifyError::Invalid(InvalidProof::Signature))
        ));
    }

    #[test]
    fn rejects_jwk_with_private_key() {
        let header = proof(NOW, None).split('.').next().unwrap().to_owned();
        let mut json: serde_json::Value =
            serde_json::from_slice(&URL_SAFE_NO_PAD.decode(header).unwrap()).unwrap();
        json["jwk"]["d"] = "c2VjcmV0".into();
        let header = URL_SAFE_NO_PAD.encode(serde_json::to_vec(&json).unwrap());
        let leaked = replace_segment(&proof(NOW, None), 0, &header);

        assert!(matches!(
            verify(&leaked, expected(None)),
            Err(VerifyError::Invalid(InvalidProof::Jwk(
                jwk::JwkError::PrivateKey
            ))),
        ));
    }

    #[test]
    fn rejects_non_base64_jwk_coordinate_without_panicking() {
        let header = proof(NOW, None).split('.').next().unwrap().to_owned();
        let mut json: serde_json::Value =
            serde_json::from_slice(&URL_SAFE_NO_PAD.decode(header).unwrap()).unwrap();
        json["jwk"]["x"] = "not base64!".into();
        let header = URL_SAFE_NO_PAD.encode(serde_json::to_vec(&json).unwrap());
        let broken = replace_segment(&proof(NOW, None), 0, &header);

        assert!(matches!(
            verify(&broken, expected(None)),
            Err(VerifyError::Invalid(InvalidProof::Jwk(
                jwk::JwkError::Base64(_)
            ))),
        ));
    }

    #[test]
    fn rejects_missing_segments() {
        assert!(matches!(
            verify("abc", expected(None)),
            Err(VerifyError::Invalid(InvalidProof::Malformed))
        ));
        assert!(matches!(
            verify("abc.def", expected(None)),
            Err(VerifyError::Invalid(InvalidProof::Malformed))
        ));
    }

    #[test]
    fn rejects_crit_header() {
        let header = proof(NOW, None).split('.').next().unwrap().to_owned();
        let mut json: serde_json::Value =
            serde_json::from_slice(&URL_SAFE_NO_PAD.decode(header).unwrap()).unwrap();
        json["crit"] = serde_json::json!(["exp"]);
        let header = URL_SAFE_NO_PAD.encode(serde_json::to_vec(&json).unwrap());
        let critical = replace_segment(&proof(NOW, None), 0, &header);

        assert!(matches!(
            verify(&critical, expected(None)),
            Err(VerifyError::Invalid(InvalidProof::Critical))
        ));
    }

    #[test]
    fn requires_nonce_until_a_window_after_start() {
        let verifier = verifier_started_at(NOW);

        required_nonce(verifier.check_at(&proof(NOW, None), expected(None), NOW));
        required_nonce(verifier.check_at(
            &proof(NOW + WINDOW - 1, None),
            expected(None),
            NOW + WINDOW - 1,
        ));
        assert!(
            verifier
                .check_at(&proof(NOW + WINDOW, None), expected(None), NOW + WINDOW)
                .is_ok()
        );
    }

    #[test]
    fn requires_nonce_for_iat_outside_window() {
        let verifier = verifier_started_at(NOW - 10 * WINDOW);

        required_nonce(verifier.check_at(&proof(NOW - WINDOW - 1, None), expected(None), NOW));
        required_nonce(verifier.check_at(&proof(NOW + WINDOW + 1, None), expected(None), NOW));
    }

    #[test]
    fn accepts_iat_at_window_edges() {
        // One verifier per proof: both carry the same jti.
        for iat in [NOW - WINDOW, NOW + WINDOW] {
            let verifier = verifier_started_at(NOW - 10 * WINDOW);

            assert!(
                verifier
                    .check_at(&proof(iat, None), expected(None), NOW)
                    .is_ok()
            );
        }
    }

    #[test]
    fn accepts_proof_carrying_issued_nonce() {
        let verifier = verifier_started_at(NOW);
        let nonce = required_nonce(verifier.check_at(&proof(NOW, None), expected(None), NOW));

        // The nonce replaces the iat check, so even a broken client clock passes.
        let proof = proof_with_nonce(0, None, Some(&nonce.to_string()));
        assert!(verifier.check_at(&proof, expected(None), NOW).is_ok());
    }

    #[test]
    fn accepts_nonce_until_it_expires() {
        let verifier = verifier_started_at(NOW);
        let nonce =
            required_nonce(verifier.check_at(&proof(NOW, None), expected(None), NOW)).to_string();
        let proof = |iat| proof_with_nonce(iat, None, Some(&nonce));
        let expires_at = NOW + NONCE_LIFETIME;

        assert!(
            verifier
                .check_at(&proof(1), expected(None), expires_at)
                .is_ok()
        );
        required_nonce(verifier.check_at(&proof(2), expected(None), expires_at + 1));
    }

    #[test]
    fn rejects_nonce_from_previous_run() {
        let previous = verifier_started_at(NOW);
        let nonce = required_nonce(previous.check_at(&proof(NOW, None), expected(None), NOW));
        let restarted = DpopVerifier::started_at(NonceKey::random(), NOW + 1);

        required_nonce(restarted.check_at(
            &proof_with_nonce(NOW, None, Some(&nonce.to_string())),
            expected(None),
            NOW + 1,
        ));
    }

    #[test]
    fn rejects_forged_nonce() {
        let verifier = verifier_started_at(NOW);

        required_nonce(verifier.check_at(
            &proof_with_nonce(NOW, None, Some("forged")),
            expected(None),
            NOW,
        ));
    }

    #[test]
    fn accepts_proof_from_bound_key() {
        assert!(verify(&proof(NOW, Some("token")), expected(Some("token"))).is_ok());
    }

    #[test]
    fn rejects_proof_from_other_than_bound_key() {
        let bound = Expected {
            bound: Some(Bound {
                access_token: "token",
                jkt: &[0; 32],
            }),
            ..expected(None)
        };

        assert!(matches!(
            verify(&proof(NOW, Some("token")), bound),
            Err(VerifyError::Invalid(InvalidProof::KeyMismatch))
        ));
    }

    #[test]
    fn rejects_replayed_proof() {
        let verifier = verifier_started_at(NOW - WINDOW);
        let proof = proof(NOW, None);

        assert!(record_at(&verifier, &proof, NOW).is_ok());
        assert!(matches!(
            record_at(&verifier, &proof, NOW),
            Err(VerifyError::Invalid(InvalidProof::Replay))
        ));
    }

    #[test]
    fn rejects_replayed_proof_carrying_nonce() {
        let verifier = verifier_started_at(NOW);
        let nonce = required_nonce(verifier.check_at(&proof(NOW, None), expected(None), NOW));
        let proof = proof_with_nonce(NOW, None, Some(&nonce.to_string()));

        assert!(record_at(&verifier, &proof, NOW).is_ok());
        assert!(matches!(
            record_at(&verifier, &proof, NOW),
            Err(VerifyError::Invalid(InvalidProof::Replay))
        ));
    }

    #[test]
    fn replay_is_refused_for_as_long_as_the_proof_is_acceptable() {
        let verifier = verifier_started_at(NOW - WINDOW);
        let proof = proof(NOW, None);
        record_at(&verifier, &proof, NOW).unwrap();

        assert!(matches!(
            record_at(&verifier, &proof, NOW + WINDOW),
            Err(VerifyError::Invalid(InvalidProof::Replay))
        ));
        required_nonce(verifier.check_at(&proof, expected(None), NOW + WINDOW + 1));
    }

    #[test]
    fn single_use_proof_does_not_take_its_jti() {
        let verifier = verifier_started_at(NOW - WINDOW);
        let proof = proof(NOW, None);

        verifier
            .check_at(&proof, expected(None), NOW)
            .unwrap()
            .skip_replay_check();

        assert!(record_at(&verifier, &proof, NOW).is_ok());
    }

    #[test]
    fn full_replay_store_is_busy() {
        let verifier = DpopVerifier {
            seen_jtis: SeenJtis::with_capacity(0),
            ..verifier_started_at(NOW - WINDOW)
        };

        assert!(matches!(
            record_at(&verifier, &proof(NOW, None), NOW),
            Err(VerifyError::Busy { .. })
        ));
    }

    #[test]
    fn rejected_proof_does_not_take_its_jti() {
        let verifier = verifier_started_at(NOW - WINDOW);
        let proof = proof(NOW, None);

        assert!(matches!(
            verifier.check_at(&proof, expected(Some("token")), NOW),
            Err(VerifyError::Invalid(InvalidProof::AccessTokenHash))
        ));
        assert!(record_at(&verifier, &proof, NOW).is_ok());
    }
}
