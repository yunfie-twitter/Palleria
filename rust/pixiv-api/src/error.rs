#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ApiError {
    #[error("invalid request: {detail}")]
    InvalidRequest { detail: String },
    #[error("network request failed: {detail}")]
    Network { detail: String },
    #[error("HTTP {status}: {detail}")]
    Http { status: u16, detail: String },
}

pub(crate) fn network_error(error: reqwest::Error) -> ApiError {
    ApiError::Network {
        detail: error.to_string(),
    }
}

pub(crate) fn http_error(status: u16, body: &str) -> ApiError {
    ApiError::Http {
        status,
        detail: if body.trim().is_empty() {
            "Pixiv API request failed".to_owned()
        } else {
            body.trim().to_owned()
        },
    }
}
