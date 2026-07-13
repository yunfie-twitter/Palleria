#[derive(Debug, uniffi::Record)]
pub struct ApiResponse {
    pub status: u16,
    pub body: String,
}

#[derive(Debug, uniffi::Record)]
pub struct LoginSession {
    pub access_token: String,
    pub refresh_token: String,
    pub user_id: Option<u64>,
}
