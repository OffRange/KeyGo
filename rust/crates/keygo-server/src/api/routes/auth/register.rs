use crate::api::dpop::SingleUseDpopProof;
use crate::api::routes::auth::session::Session;
use crate::api::{AppState, ErrorCode, HttpError, Json, ServerResult};
use crate::domain::{DeviceInfo, RegistrationId, Username};
use crate::service::registration::{self, FinishRegistration, StartRegistration};
use axum::Router;
use axum::extract::State;
use axum::http::StatusCode;
use axum::routing::post;
use keygo_opaque::{RegistrationRequest, RegistrationResponse, RegistrationUpload};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/start", post(start))
        .route("/finish", post(finish))
}

#[derive(Deserialize)]
struct StartRequest {
    /// Parsed in the handler so a bad name answers `invalid_username`, not `invalid_request`.
    username: String,
    user_id: Uuid,
    invite: Option<String>,
    registration_request: RegistrationRequest,
}

#[derive(Serialize)]
struct StartResponse {
    registration_id: RegistrationId,
    registration_response: RegistrationResponse,
    expires_in: u64,
}

impl HttpError for registration::StartError {
    fn code(&self) -> ErrorCode {
        match self {
            Self::InvalidUserId => ErrorCode::InvalidRequest,
            Self::ReservedUsername => ErrorCode::InvalidUsername,
            Self::InviteRequired => ErrorCode::InviteRequired,
            Self::InviteInvalid => ErrorCode::InviteInvalid,
            Self::UsernameTaken => ErrorCode::UsernameTaken,
            Self::UserIdTaken => ErrorCode::UserIdTaken,
            Self::Opaque(err) => err.code(),
            Self::Db(_) => ErrorCode::Internal,
        }
    }
}

async fn start(
    State(state): State<AppState>,
    Json(request): Json<StartRequest>,
) -> ServerResult<Json<StartResponse>> {
    let input = StartRegistration {
        username: Username::parse(&request.username)?,
        user_id: request.user_id,
        invite: request.invite,
        registration_request: request.registration_request,
    };

    let started = registration::start(&state.db, &state.opaque_state, &state.signup, input).await?;

    Ok(Json(StartResponse {
        registration_id: started.registration_id,
        registration_response: started.registration_response,
        expires_in: started.expires_in.as_secs(),
    }))
}

#[derive(Deserialize)]
struct FinishRequest {
    registration_id: RegistrationId,
    registration_upload: RegistrationUpload,
    device: DeviceInfo,
}

#[derive(Serialize)]
struct FinishResponse {
    username: Username,
    session: Session,
}

impl HttpError for registration::FinishError {
    fn code(&self) -> ErrorCode {
        match self {
            Self::Expired => ErrorCode::RegistrationExpired,
            Self::InviteInvalid => ErrorCode::InviteInvalid,
            Self::Db(_) => ErrorCode::Internal,
        }
    }
}

async fn finish(
    State(state): State<AppState>,
    proof: SingleUseDpopProof,
    Json(request): Json<FinishRequest>,
) -> ServerResult<(StatusCode, Json<FinishResponse>)> {
    let input = FinishRegistration {
        registration_id: request.registration_id,
        registration_upload: request.registration_upload,
        device_info: request.device,
        jkt: proof.jkt,
    };

    let finished = registration::finish(&state.db, &state.token_issuer, input).await?;

    Ok((
        StatusCode::CREATED,
        Json(FinishResponse {
            username: finished.username,
            session: finished.session.into(),
        }),
    ))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::api::error::render;
    use crate::testing::{
        app_state, count, create_invite, device_json, invite_only, post, post_to, post_with_proof,
        proof_post, prover, register, replayable_post, send,
    };
    use keygo_opaque::client::ClientRegistration;
    use keygo_opaque::server::ServerSetup;
    use serde_json::{Value, json};
    use sqlx::PgPool;

    const START: &str = "/api/v1/auth/register/start";
    const FINISH: &str = "/api/v1/auth/register/finish";

    fn start_body(username: &str, user_id: Uuid) -> (ClientRegistration, Value) {
        let (client, request) = ClientRegistration::start(crate::testing::PASSWORD).unwrap();
        let body = json!({
            "username": username,
            "user_id": user_id,
            "registration_request": request.to_base64(),
        });
        (client, body)
    }

    fn finish_body(client: ClientRegistration, started: &Value) -> Value {
        let response =
            RegistrationResponse::from_base64(started["registration_response"].as_str().unwrap())
                .unwrap();
        let upload = client
            .finish(crate::testing::PASSWORD, response)
            .unwrap()
            .upload;
        json!({
            "registration_id": started["registration_id"],
            "registration_upload": upload.to_base64(),
            "device": device_json(),
        })
    }

    #[tokio::test]
    async fn expired_registrations_are_bad_requests() {
        let (status, body) = render(registration::FinishError::Expired).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "registration_expired");
    }

    #[tokio::test]
    async fn taken_usernames_conflict() {
        let (status, body) = render(registration::StartError::UsernameTaken).await;

        assert_eq!(status, StatusCode::CONFLICT);
        assert_eq!(body["code"], "username_taken");
    }

    #[sqlx::test]
    async fn registers_over_http(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let (client, body) = start_body("Ada", Uuid::new_v4());

        let (status, started) = post_to(state.clone(), START, body).await;

        assert_eq!(status, StatusCode::OK);
        assert_eq!(started["expires_in"], registration::TTL.as_secs());
        let body = finish_body(client, &started);

        let (status, finished) = post_with_proof(&state, &prover(), FINISH, body).await;

        assert_eq!(status, StatusCode::CREATED);
        assert_eq!(finished["username"], "ada");
        assert_eq!(count(&db, "users").await, 1);
        assert_eq!(count(&db, "devices").await, 1);
        assert_eq!(count(&db, "sessions").await, 1);
    }

    #[sqlx::test]
    async fn finish_returns_a_session_bound_to_the_proof_key(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let prover = prover();
        let (client, body) = start_body("ada", Uuid::new_v4());
        let (_, started) = post_to(state.clone(), START, body).await;

        let (status, finished) =
            post_with_proof(&state, &prover, FINISH, finish_body(client, &started)).await;

        assert_eq!(status, StatusCode::CREATED);
        let session = &finished["session"];
        let device_id: Uuid = sqlx::query_scalar("select id from devices")
            .fetch_one(&db)
            .await
            .unwrap();
        assert_eq!(session["device_id"], device_id.to_string());
        assert_eq!(
            session["refresh_expires_in"],
            crate::domain::REFRESH_TTL.as_secs(),
        );
        let token = session["refresh_token"].as_str().unwrap();
        assert!(token.starts_with("kgr_"), "{token}");
        assert_eq!(token.len(), "kgr_".len() + 43);
        let jkt: Vec<u8> = sqlx::query_scalar("select dpop_jkt from sessions")
            .fetch_one(&db)
            .await
            .unwrap();
        assert_eq!(jkt, prover.jkt());
        assert_eq!(
            session["access_expires_in"],
            crate::domain::ACCESS_TOKEN_LIFETIME.as_secs(),
        );
        let claims = state
            .token_issuer
            .verify(session["access_token"].as_str().unwrap())
            .unwrap();
        assert_eq!(claims.jkt(), prover.jkt());
    }

    fn invite_only_state(db: &PgPool, setup: &ServerSetup) -> AppState {
        AppState {
            signup: invite_only(),
            ..app_state(db, setup)
        }
    }

    #[sqlx::test]
    async fn registers_with_an_invite_over_http(db: PgPool) {
        let (client, mut body) = start_body("ada", Uuid::new_v4());
        body["invite"] = create_invite(&db).await.into();

        let state = invite_only_state(&db, &ServerSetup::generate());

        let (status, started) = post_to(state.clone(), START, body).await;

        assert_eq!(status, StatusCode::OK);
        let body = finish_body(client, &started);

        let (status, _) = post_with_proof(&state, &prover(), FINISH, body).await;

        assert_eq!(status, StatusCode::CREATED);
        assert_eq!(count(&db, "users").await, 1);
        assert_eq!(count(&db, "invites").await, 0);
    }

    #[sqlx::test]
    async fn invite_only_start_without_an_invite_is_forbidden(db: PgPool) {
        let (_, body) = start_body("ada", Uuid::new_v4());

        let (status, body) = post_to(
            invite_only_state(&db, &ServerSetup::generate()),
            START,
            body,
        )
        .await;

        assert_eq!(status, StatusCode::FORBIDDEN);
        assert_eq!(body["code"], "invite_required");
    }

    #[sqlx::test]
    async fn invite_only_start_with_an_unknown_invite_is_forbidden(db: PgPool) {
        let (_, mut body) = start_body("ada", Uuid::new_v4());
        body["invite"] = "7KQ2M9XD4HPA".into();

        let (status, body) = post_to(
            invite_only_state(&db, &ServerSetup::generate()),
            START,
            body,
        )
        .await;

        assert_eq!(status, StatusCode::FORBIDDEN);
        assert_eq!(body["code"], "invite_invalid");
    }

    #[tokio::test]
    async fn invite_spent_meanwhile_is_forbidden_at_finish() {
        let (status, body) = render(registration::FinishError::InviteInvalid).await;

        assert_eq!(status, StatusCode::FORBIDDEN);
        assert_eq!(body["code"], "invite_invalid");
    }

    #[sqlx::test]
    async fn start_rejects_an_invalid_username(db: PgPool) {
        let (_, body) = start_body("a", Uuid::new_v4());

        let (status, body) = post(&db, &ServerSetup::generate(), START, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_username");
    }

    #[sqlx::test]
    async fn start_rejects_a_reserved_username(db: PgPool) {
        let (_, body) = start_body("admin", Uuid::new_v4());

        let (status, body) = post(&db, &ServerSetup::generate(), START, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_username");
    }

    #[sqlx::test]
    async fn start_rejects_a_structured_user_id(db: PgPool) {
        let (_, body) = start_body("ada", Uuid::nil());

        let (status, body) = post(&db, &ServerSetup::generate(), START, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_request");
    }

    #[sqlx::test]
    async fn start_rejects_a_malformed_registration_request(db: PgPool) {
        let body = json!({
            "username": "ada",
            "user_id": Uuid::new_v4(),
            "registration_request": "AAAA",
        });

        let (status, body) = post(&db, &ServerSetup::generate(), START, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_request");
    }

    #[sqlx::test]
    async fn start_conflicts_on_a_taken_username(db: PgPool) {
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (_, body) = start_body("ADA", Uuid::new_v4());

        let (status, body) = post(&db, &setup, START, body).await;

        assert_eq!(status, StatusCode::CONFLICT);
        assert_eq!(body["code"], "username_taken");
    }

    #[sqlx::test]
    async fn retry_after_a_lost_201_conflicts_on_the_user_id(db: PgPool) {
        let setup = ServerSetup::generate();
        let user_id = register(&db, &setup, "ada").await;
        let (_, body) = start_body("ada", user_id);

        let (status, body) = post(&db, &setup, START, body).await;

        assert_eq!(status, StatusCode::CONFLICT);
        assert_eq!(body["code"], "user_id_taken");
    }

    #[sqlx::test]
    async fn start_conflicts_on_a_taken_user_id(db: PgPool) {
        let setup = ServerSetup::generate();
        let user_id = register(&db, &setup, "ada").await;
        let (_, body) = start_body("bob", user_id);

        let (status, body) = post(&db, &setup, START, body).await;

        assert_eq!(status, StatusCode::CONFLICT);
        assert_eq!(body["code"], "user_id_taken");
    }

    #[sqlx::test]
    async fn finish_rejects_an_unknown_registration(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let (client, body) = start_body("ada", Uuid::new_v4());
        let (_, started) = post_to(state.clone(), START, body).await;
        let mut body = finish_body(client, &started);
        body["registration_id"] = json!(RegistrationId::random());

        let (status, body) = post_with_proof(&state, &prover(), FINISH, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "registration_expired");
        assert_eq!(count(&db, "users").await, 0);
    }

    #[sqlx::test]
    async fn finish_rejects_a_spoofing_device_name(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let (client, body) = start_body("ada", Uuid::new_v4());
        let (_, started) = post_to(state.clone(), START, body).await;
        let mut body = finish_body(client, &started);
        body["device"]["name"] = "Pixel\u{202E}9".into();

        let (status, body) = post_with_proof(&state, &prover(), FINISH, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_request");
        assert_eq!(count(&db, "pending_registrations").await, 1);
    }

    #[sqlx::test]
    async fn finish_without_a_proof_keeps_the_registration(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let (client, body) = start_body("ada", Uuid::new_v4());
        let (_, started) = post_to(state.clone(), START, body).await;
        let body = finish_body(client, &started);

        let (status, response) = post_to(state.clone(), FINISH, body.clone()).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(response["code"], "invalid_dpop_proof");
        assert_eq!(count(&db, "pending_registrations").await, 1);

        let (status, _) = post_with_proof(&state, &prover(), FINISH, body).await;

        assert_eq!(status, StatusCode::CREATED);
    }

    /// Finish skips the jti check: the taken reservation already turns a replay away.
    #[sqlx::test]
    async fn replayed_finish_is_expired(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let (client, body) = start_body("ada", Uuid::new_v4());
        let (_, started) = post_to(state.clone(), START, body).await;
        let body = finish_body(client, &started);
        let replay = replayable_post(&state, &prover(), FINISH, &body).await;

        let (accepted, _, _) = send(&state, replay()).await;
        let (status, _, body) = send(&state, replay()).await;

        assert_eq!(accepted, StatusCode::CREATED);
        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "registration_expired");
        assert_eq!(count(&db, "users").await, 1);
        assert_eq!(count(&db, "sessions").await, 1);
    }

    #[sqlx::test]
    async fn nonce_challenge_keeps_the_registration(db: PgPool) {
        let state = app_state(&db, &ServerSetup::generate());
        let (client, body) = start_body("ada", Uuid::new_v4());
        let (_, started) = post_to(state.clone(), START, body).await;
        let body = finish_body(client, &started);
        let prover = prover();

        // A verifier that just started accepts no proof without one of its own nonces.
        let request = proof_post(&state, &prover, FINISH, &body, None);
        let (status, headers, challenge) = send(&state, request).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(challenge["code"], "use_dpop_nonce");
        assert!(headers.contains_key("dpop-nonce"));
        assert_eq!(count(&db, "pending_registrations").await, 1);

        let (status, _) = post_with_proof(&state, &prover, FINISH, body).await;

        assert_eq!(status, StatusCode::CREATED);
        assert_eq!(count(&db, "users").await, 1);
    }
}
