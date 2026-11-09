use std::time::Duration;

pub const SESSION_LIFETIME: Duration = Duration::from_hours(180 * 24);
pub const REFRESH_TTL: Duration = Duration::from_hours(30 * 24);
pub const SESSION_RETENTION: Duration = Duration::from_hours(30 * 24);
