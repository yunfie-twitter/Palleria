use std::collections::HashMap;
use std::net::SocketAddr;
use std::time::Duration;

use reqwest::blocking::Client;
use reqwest::header::{HeaderMap, HeaderName, HeaderValue, CONTENT_TYPE};

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ApiError {
    #[error("invalid request: {detail}")]
    InvalidRequest { detail: String },
    #[error("network request failed: {detail}")]
    Network { detail: String },
    #[error("HTTP {status}: {detail}")]
    Http { status: u16, detail: String },
}

#[derive(Debug, uniffi::Record)]
pub struct ApiResponse {
    pub status: u16,
    pub body: Vec<u8>,
}

#[derive(uniffi::Object)]
pub struct PixivHttpClient {
    client: Client,
}

#[uniffi::export]
impl PixivHttpClient {
    #[uniffi::constructor]
    pub fn new(network_mode: String) -> Result<Self, ApiError> {
        let mut builder = Client::builder()
            .connect_timeout(Duration::from_secs(12))
            .timeout(Duration::from_secs(30))
            .pool_max_idle_per_host(6)
            .pool_idle_timeout(Duration::from_secs(300));

        if network_mode != "standard" {
            builder = builder
                .danger_accept_invalid_certs(true)
                .danger_accept_invalid_hostnames(true);
        }
        if network_mode == "ech" {
            for host in ["app-api.pixiv.net", "oauth.secure.pixiv.net", "accounts.pixiv.net"] {
                for ip in ["104.18.10.118", "104.18.11.118"] {
                    let address: SocketAddr = format!("{ip}:443").parse().map_err(|error| {
                        ApiError::InvalidRequest { detail: format!("invalid compatible endpoint: {error}") }
                    })?;
                    builder = builder.resolve(host, address);
                }
            }
        }

        let client = builder.build().map_err(network_error)?;
        Ok(Self { client })
    }

    pub fn execute(
        &self,
        method: String,
        url: String,
        headers: HashMap<String, String>,
        body: Vec<u8>,
        content_type: Option<String>,
    ) -> Result<ApiResponse, ApiError> {
        let method = reqwest::Method::from_bytes(method.as_bytes()).map_err(|error| {
            ApiError::InvalidRequest { detail: format!("invalid HTTP method: {error}") }
        })?;
        let mut header_map = HeaderMap::new();
        for (name, value) in headers {
            let name = HeaderName::from_bytes(name.as_bytes()).map_err(|error| {
                ApiError::InvalidRequest { detail: format!("invalid header name: {error}") }
            })?;
            let value = HeaderValue::from_str(&value).map_err(|error| {
                ApiError::InvalidRequest { detail: format!("invalid header value: {error}") }
            })?;
            header_map.append(name, value);
        }
        if let Some(value) = content_type {
            let value = HeaderValue::from_str(&value).map_err(|error| ApiError::InvalidRequest {
                detail: format!("invalid content type: {error}"),
            })?;
            header_map.insert(CONTENT_TYPE, value);
        }

        let mut request = self.client.request(method, &url).headers(header_map);
        if !body.is_empty() {
            request = request.body(body);
        }
        let response = request.send().map_err(network_error)?;
        let status = response.status().as_u16();
        let bytes = response.bytes().map_err(network_error)?.to_vec();
        if !(200..300).contains(&status) {
            let detail = pixiv_error_message(&bytes);
            return Err(ApiError::Http { status, detail });
        }
        Ok(ApiResponse { status, body: bytes })
    }
}

fn network_error(error: reqwest::Error) -> ApiError {
    ApiError::Network { detail: error.to_string() }
}

fn pixiv_error_message(body: &[u8]) -> String {
    let text = String::from_utf8_lossy(body).trim().to_owned();
    if text.is_empty() { "Pixiv API request failed".to_owned() } else { text }
}

uniffi::setup_scaffolding!();

#[cfg(test)]
mod tests {
    use super::*;
    use std::thread;

    fn server(status: u16, body: &'static str) -> String {
        let server = tiny_http::Server::http("127.0.0.1:0").unwrap();
        let address = format!("http://{}", server.server_addr());
        thread::spawn(move || {
            let request = server.recv().unwrap();
            request.respond(tiny_http::Response::from_string(body).with_status_code(status)).unwrap();
        });
        address
    }

    #[test]
    fn returns_success_body() {
        let client = PixivHttpClient::new("standard".into()).unwrap();
        let response = client.execute("GET".into(), server(200, "{\"ok\":true}"), HashMap::new(), vec![], None).unwrap();
        assert_eq!(response.status, 200);
        assert_eq!(response.body, br#"{"ok":true}"#);
    }

    #[test]
    fn exposes_http_status_and_body() {
        let client = PixivHttpClient::new("standard".into()).unwrap();
        let error = client.execute("GET".into(), server(403, "denied"), HashMap::new(), vec![], None).unwrap_err();
        assert!(matches!(error, ApiError::Http { status: 403, detail } if detail == "denied"));
    }
}
