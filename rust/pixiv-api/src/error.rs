#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ApiError {
    #[error("invalid request: {detail}")]
    InvalidRequest { detail: String },
    #[error("network request failed: {detail}")]
    Network { detail: String },
    #[error("HTTP {status}: {detail}")]
    Http { status: u16, detail: String },
    #[error("invalid response: {detail}")]
    InvalidResponse { detail: String },
}

pub(crate) fn network_error(error: reqwest::Error) -> ApiError {
    ApiError::Network {
        detail: error.to_string(),
    }
}

pub(crate) fn invalid_request(detail: impl Into<String>) -> ApiError {
    ApiError::InvalidRequest {
        detail: detail.into(),
    }
}

pub(crate) fn invalid_response(detail: impl Into<String>) -> ApiError {
    ApiError::InvalidResponse {
        detail: detail.into(),
    }
}

pub(crate) fn io_error(context: &str, error: std::io::Error) -> ApiError {
    ApiError::Network {
        detail: format!("{context}: {error}"),
    }
}

pub(crate) fn http_error(status: u16, body: &str) -> ApiError {
    let trimmed = body.trim();
    ApiError::Http {
        status,
        detail: if trimmed.is_empty() {
            "Pixiv API request failed".to_owned()
        } else {
            trimmed.to_owned()
        },
    }
}
