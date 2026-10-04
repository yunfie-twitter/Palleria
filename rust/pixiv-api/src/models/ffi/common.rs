use std::collections::HashMap;

#[derive(Debug, uniffi::Record)]
pub struct PixivRequest {
    pub method: String,
    pub url: String,
    pub headers: HashMap<String, String>,
    pub body: Vec<u8>,
    pub content_type: Option<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct LoginSession {
    pub expires_in: Option<u64>,
    pub access_token: String,
    pub refresh_token: String,
    pub user_id: Option<u64>,
}

#[derive(Debug, uniffi::Record)]
pub struct StringList {
    pub items: Vec<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct OptionalBoolean {
    pub value: Option<bool>,
}
