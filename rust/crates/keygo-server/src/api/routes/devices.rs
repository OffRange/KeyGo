use crate::api::authenticated::Authenticated;
use crate::api::{AppState, Json, ServerResult};
use crate::domain::{Device, DeviceId};
use crate::service::device;
use axum::Router;
use axum::extract::{Path, State};
use axum::routing::{delete, get};
use serde::Serialize;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/", get(devices))
        .route("/{id}", delete(delete_devices))
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
) -> ServerResult<()> {
    Ok(())
}
