use crate::api::authenticated::Authenticated;
use crate::api::{AppState, ErrorCode, HttpError, Json, ServerResult};
use crate::domain::{Device, DeviceId};
use crate::service::device;
use crate::service::device::DeleteDeviceError;
use axum::Router;
use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::routing::{delete, get};
use serde::Serialize;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/", get(devices))
        .route("/{id}", delete(delete_devices))
}

impl HttpError for DeleteDeviceError {
    fn code(&self) -> ErrorCode {
        match self {
            Self::FreshAuthRequired => ErrorCode::FreshAuthRequired,
            Self::Db(_) => ErrorCode::Internal,
        }
    }
}

#[derive(Serialize)]
struct Devices {
    devices: Vec<Device>,
}

async fn devices(
    State(state): State<AppState>,
    authenticated: Authenticated,
) -> ServerResult<Json<Devices>> {
    let devices = device::get_devices(&state.db, authenticated.claims().user_id()).await?;
    Ok(Json(Devices { devices }))
}

async fn delete_devices(
    State(state): State<AppState>,
    Path(id): Path<DeviceId>,
    authenticated: Authenticated,
) -> ServerResult<StatusCode> {
    device::delete_device(&state.db, id, authenticated.claims()).await?;
    Ok(StatusCode::NO_CONTENT)
}
