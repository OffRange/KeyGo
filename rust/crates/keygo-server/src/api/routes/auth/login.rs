use crate::api::{AppState, ErrorCode, HttpError, Json, ServerResult};
use crate::domain::{LoginId, Username};
use crate::service::login::{self, FinishLogin, StartLogin};
use axum::Router;
use axum::extract::State;
use axum::http::StatusCode;
use axum::routing::post;
use keygo_opaque::{CredentialFinalization, CredentialRequest, CredentialResponse};
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
    credential_request: CredentialRequest,
}

#[derive(Serialize)]
struct StartResponse {
    login_id: LoginId,
    credential_response: CredentialResponse,
    expires_in: u64,
}

impl HttpError for login::StartError {
    fn status_and_code(&self) -> (StatusCode, ErrorCode) {
        match self {
            Self::Opaque(err) => err.status_and_code(),
            Self::Db(err) => err.status_and_code(),
        }
    }
}

async fn start(
    State(state): State<AppState>,
    Json(request): Json<StartRequest>,
) -> ServerResult<Json<StartResponse>> {
    let input = StartLogin {
        username: Username::parse(&request.username)?,
        credential_request: request.credential_request,
    };

    let started = login::start(&state.db, &state.opaque_state, input).await?;

    Ok(Json(StartResponse {
        login_id: started.login_id,
        credential_response: started.credential_response,
        expires_in: started.expires_in.as_secs(),
    }))
}

#[derive(Deserialize)]
struct FinishRequest {
    login_id: LoginId,
    credential_finalization: CredentialFinalization,
}

#[derive(Serialize)]
struct FinishResponse {
    user_id: Uuid,
    username: Username,
}

impl HttpError for login::FinishError {
    fn status_and_code(&self) -> (StatusCode, ErrorCode) {
        match self {
            Self::Expired => (StatusCode::BAD_REQUEST, ErrorCode::LoginExpired),
            Self::Opaque(err) => err.status_and_code(),
            Self::Db(err) => err.status_and_code(),
        }
    }
}

async fn finish(
    State(state): State<AppState>,
    Json(request): Json<FinishRequest>,
) -> ServerResult<Json<FinishResponse>> {
    let input = FinishLogin {
        login_id: request.login_id,
        credential_finalization: request.credential_finalization,
    };

    let finished = login::finish(&state.db, input).await?;

    Ok(Json(FinishResponse {
        user_id: finished.user_id,
        username: finished.username,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::api::error::render;
    use crate::testing::{PASSWORD, count, post, register};
    use keygo_opaque::OpaqueError;
    use keygo_opaque::client::ClientLogin;
    use keygo_opaque::server::ServerSetup;
    use serde_json::{Value, json};
    use sqlx::PgPool;

    const START: &str = "/api/v1/auth/login/start";
    const FINISH: &str = "/api/v1/auth/login/finish";

    async fn start_over_http(
        db: &PgPool,
        setup: &ServerSetup,
        username: &str,
    ) -> (ClientLogin, Value) {
        let (client, request) = ClientLogin::start(PASSWORD).unwrap();
        let body = json!({ "username": username, "credential_request": request.to_base64() });
        let (status, started) = post(db, setup, START, body).await;
        assert_eq!(status, StatusCode::OK);
        (client, started)
    }

    fn finalization(client: ClientLogin, started: &Value) -> String {
        let response =
            CredentialResponse::from_base64(started["credential_response"].as_str().unwrap())
                .unwrap();
        client
            .finish(PASSWORD, response)
            .unwrap()
            .finalization
            .to_base64()
    }

    #[tokio::test]
    async fn expired_logins_are_bad_requests() {
        let (status, body) = render(login::FinishError::Expired).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "login_expired");
    }

    #[tokio::test]
    async fn failed_login_finish_matches_failed_start() {
        let (status, body) = render(login::FinishError::Opaque(OpaqueError::InvalidLogin)).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_credentials");
    }

    #[sqlx::test]
    async fn logs_in_over_http(db: PgPool) {
        let setup = ServerSetup::generate();
        let user_id = register(&db, &setup, "ada").await;
        let (client, started) = start_over_http(&db, &setup, "Ada").await;

        assert_eq!(started["expires_in"], login::TTL.as_secs());
        let body = json!({
            "login_id": started["login_id"],
            "credential_finalization": finalization(client, &started),
        });

        let (status, finished) = post(&db, &setup, FINISH, body).await;

        assert_eq!(status, StatusCode::OK);
        assert_eq!(finished, json!({ "user_id": user_id, "username": "ada" }));
        assert_eq!(count(&db, "login_states").await, 0);
    }

    #[sqlx::test]
    async fn unknown_user_starts_like_a_known_one(db: PgPool) {
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;

        let (_, known) = start_over_http(&db, &setup, "ada").await;
        let (_, unknown) = start_over_http(&db, &setup, "bob").await;

        let shape = |body: &Value| {
            body.as_object()
                .unwrap()
                .iter()
                .map(|(key, value)| (key.clone(), value.as_str().map(str::len)))
                .collect::<Vec<_>>()
        };
        assert_eq!(shape(&known), shape(&unknown));
    }

    #[sqlx::test]
    async fn start_rejects_an_invalid_username(db: PgPool) {
        let (_, request) = ClientLogin::start(PASSWORD).unwrap();
        let body = json!({ "username": "a", "credential_request": request.to_base64() });

        let (status, body) = post(&db, &ServerSetup::generate(), START, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_username");
    }

    #[sqlx::test]
    async fn start_rejects_a_malformed_credential_request(db: PgPool) {
        let body = json!({ "username": "ada", "credential_request": "AAAA" });

        let (status, body) = post(&db, &ServerSetup::generate(), START, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_request");
        assert_eq!(count(&db, "login_states").await, 0);
    }

    #[sqlx::test]
    async fn foreign_finalization_is_unauthorized(db: PgPool) {
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (_, target) = start_over_http(&db, &setup, "ada").await;
        let (client, other) = start_over_http(&db, &setup, "ada").await;
        let body = json!({
            "login_id": target["login_id"],
            "credential_finalization": finalization(client, &other),
        });

        let (status, body) = post(&db, &setup, FINISH, body).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_credentials");
    }

    #[sqlx::test]
    async fn unknown_user_finish_is_unauthorized(db: PgPool) {
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (_, fake) = start_over_http(&db, &setup, "bob").await;
        let (client, real) = start_over_http(&db, &setup, "ada").await;
        let body = json!({
            "login_id": fake["login_id"],
            "credential_finalization": finalization(client, &real),
        });

        let (status, body) = post(&db, &setup, FINISH, body).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert_eq!(body["code"], "invalid_credentials");
    }

    #[sqlx::test]
    async fn finish_rejects_an_unknown_login(db: PgPool) {
        let setup = ServerSetup::generate();
        register(&db, &setup, "ada").await;
        let (client, started) = start_over_http(&db, &setup, "ada").await;
        let body = json!({
            "login_id": LoginId::random(),
            "credential_finalization": finalization(client, &started),
        });

        let (status, body) = post(&db, &setup, FINISH, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "login_expired");
    }

    #[sqlx::test]
    async fn finish_rejects_a_malformed_login_id(db: PgPool) {
        let body = json!({ "login_id": "short", "credential_finalization": "AAAA" });

        let (status, body) = post(&db, &ServerSetup::generate(), FINISH, body).await;

        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body["code"], "invalid_request");
    }
}
