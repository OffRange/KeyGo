use crate::api::error::ServerError;
use axum::extract::FromRequest;
use axum::response::{IntoResponse, Response};

#[derive(FromRequest)]
#[from_request(via(axum::Json), rejection(ServerError))]
pub struct Json<T>(pub T);

impl<T> IntoResponse for Json<T>
where
    axum::Json<T>: IntoResponse,
{
    fn into_response(self) -> Response {
        axum::Json(self.0).into_response()
    }
}
