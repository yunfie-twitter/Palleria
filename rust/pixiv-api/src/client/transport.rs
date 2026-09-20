use std::collections::HashMap;

use futures_util::StreamExt;
use reqwest::header::{CONTENT_TYPE, HeaderMap, HeaderName, HeaderValue};
use reqwest::{RequestBuilder, Response};
use serde::de::DeserializeOwned;

use super::PixivHttpClient;
use crate::error::{ApiError, http_error, invalid_request, invalid_response, network_error};
use crate::models::PixivRequest;

pub(super) const JSON_RESPONSE_LIMIT: u64 = 16 * 1024 * 1024;
pub(super) const HTML_RESPONSE_LIMIT: u64 = 8 * 1024 * 1024;
const ERROR_RESPONSE_LIMIT: u64 = 64 * 1024;
const ERROR_DETAIL_LIMIT: usize = 4 * 1024;
const NO_CONTENT_DRAIN_LIMIT: u64 = 64 * 1024;

pub(super) async fn execute_json<T: DeserializeOwned>(
    client: &PixivHttpClient,
    request: PixivRequest,
    context: &str,
) -> Result<T, ApiError> {
    send_json(build_request(client, request)?, context).await
}

pub(super) async fn execute_text(
    client: &PixivHttpClient,
    request: PixivRequest,
    context: &str,
    limit: u64,
) -> Result<String, ApiError> {
    send_text(build_request(client, request)?, context, limit).await
}

pub(super) async fn execute_no_content(
    client: &PixivHttpClient,
    request: PixivRequest,
) -> Result<(), ApiError> {
    send_no_content(build_request(client, request)?).await
}

fn build_request(
    client: &PixivHttpClient,
    request: PixivRequest,
) -> Result<RequestBuilder, ApiError> {
    let method = reqwest::Method::from_bytes(request.method.as_bytes())
        .map_err(|error| invalid_request(format!("invalid HTTP method: {error}")))?;
    let headers = request_headers(client, request.headers, request.content_type.as_deref())?;
    let mut builder = client.client.request(method, request.url).headers(headers);
    if !request.body.is_empty() {
        builder = builder.body(request.body);
    }
    Ok(builder)
}

pub(super) fn request_headers(
    client: &PixivHttpClient,
    headers: HashMap<String, String>,
    content_type: Option<&str>,
) -> Result<HeaderMap, ApiError> {
    let mut header_map = HeaderMap::with_capacity(headers.len() + 9);
    for (name, value) in headers {
        let name = HeaderName::from_bytes(name.as_bytes())
            .map_err(|error| invalid_request(format!("invalid header name: {error}")))?;
        let value = HeaderValue::from_str(&value)
            .map_err(|error| invalid_request(format!("invalid header value: {error}")))?;
        header_map.append(name, value);
    }
    if let Some(value) = content_type {
        let value = HeaderValue::from_str(value)
            .map_err(|error| invalid_request(format!("invalid content type: {error}")))?;
        header_map.insert(CONTENT_TYPE, value);
    }
    client.headers.for_request(header_map)
}

pub(super) async fn send_json<T: DeserializeOwned>(
    request: RequestBuilder,
    context: &str,
) -> Result<T, ApiError> {
    let response = ensure_success(request.send().await.map_err(network_error)?).await?;
    reject_declared_size(&response, JSON_RESPONSE_LIMIT, context)?;
    let bytes = read_limited_bytes(response, JSON_RESPONSE_LIMIT, context).await?;
    serde_json::from_slice(&bytes)
        .map_err(|error| invalid_response(format!("invalid {context} response: {error}")))
}

pub(super) async fn send_text(
    request: RequestBuilder,
    context: &str,
    limit: u64,
) -> Result<String, ApiError> {
    let response = ensure_success(request.send().await.map_err(network_error)?).await?;
    reject_declared_size(&response, limit, context)?;
    let bytes = read_limited_bytes(response, limit, context).await?;
    String::from_utf8(bytes)
        .map_err(|error| invalid_response(format!("invalid {context} response body: {error}")))
}

pub(super) async fn send_no_content(request: RequestBuilder) -> Result<(), ApiError> {
    let response = ensure_success(request.send().await.map_err(network_error)?).await?;
    let mut stream = response.bytes_stream();
    let mut drained = 0;
    while let Some(chunk) = stream.next().await {
        if let Ok(chunk) = chunk {
            drained += chunk.len() as u64;
            if drained > NO_CONTENT_DRAIN_LIMIT {
                break;
            }
        } else {
            break;
        }
    }
    Ok(())
}

pub(super) async fn ensure_success(response: Response) -> Result<Response, ApiError> {
    let status = response.status().as_u16();
    if (200..300).contains(&status) {
        return Ok(response);
    }
    let body = read_limited_bytes(response, ERROR_RESPONSE_LIMIT, "error")
        .await
        .unwrap_or_default();
    Err(http_error(status, &error_preview(&body)))
}

fn reject_declared_size(response: &Response, limit: u64, context: &str) -> Result<(), ApiError> {
    if response
        .content_length()
        .is_some_and(|length| length > limit)
    {
        return Err(response_too_large(context, limit));
    }
    Ok(())
}

pub(super) async fn read_limited_bytes(
    response: Response,
    limit: u64,
    context: &str,
) -> Result<Vec<u8>, ApiError> {
    let capacity_hint = response
        .content_length()
        .filter(|&len| len <= limit)
        .unwrap_or(0) as usize;
    let mut bytes = Vec::with_capacity(capacity_hint);
    let mut stream = response.bytes_stream();
    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(network_error)?;
        if bytes.len() as u64 + chunk.len() as u64 > limit {
            return Err(response_too_large(context, limit));
        }
        bytes.extend_from_slice(&chunk);
    }
    Ok(bytes)
}

fn response_too_large(context: &str, limit: u64) -> ApiError {
    invalid_response(format!(
        "{context} response exceeds the {} MiB limit",
        limit / (1024 * 1024)
    ))
}

fn error_preview(body: &[u8]) -> String {
    let text = String::from_utf8_lossy(body);
    let mut normalized = String::new();
    for word in text.split_whitespace() {
        if !normalized.is_empty() {
            normalized.push(' ');
        }
        normalized.push_str(word);
        if normalized.len() >= ERROR_DETAIL_LIMIT * 2 {
            break;
        }
    }
    if normalized.is_empty() {
        return "Pixiv API request failed".to_owned();
    }
    truncate_utf8(&normalized, ERROR_DETAIL_LIMIT)
}

fn truncate_utf8(value: &str, max_bytes: usize) -> String {
    if value.len() <= max_bytes {
        return value.to_owned();
    }
    let mut end = max_bytes;
    while !value.is_char_boundary(end) {
        end -= 1;
    }
    format!("{}…", &value[..end])
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use std::net::TcpListener;
    use std::thread;

    fn raw_server(response: Vec<u8>) -> String {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = format!("http://{}", listener.local_addr().unwrap());
        thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            let mut request = [0_u8; 1024];
            let _ = std::io::Read::read(&mut stream, &mut request);
            stream.write_all(&response).unwrap();
        });
        address
    }

    fn response(status: &str, headers: &str, body: &[u8]) -> Vec<u8> {
        let mut response =
            format!("HTTP/1.1 {status}\r\nConnection: close\r\n{headers}\r\n").into_bytes();
        response.extend_from_slice(body);
        response
    }

    fn client() -> PixivHttpClient {
        PixivHttpClient::new(
            "standard".into(),
            "test".into(),
            "Android 10".into(),
            "en-US".into(),
        )
        .unwrap()
    }

    #[test]
    fn error_preview_is_bounded_and_utf8_safe() {
        let body = "界".repeat(ERROR_DETAIL_LIMIT);
        let preview = error_preview(body.as_bytes());
        assert!(preview.len() <= ERROR_DETAIL_LIMIT + "…".len());
        assert!(preview.ends_with('…'));
    }

    #[tokio::test]
    async fn accepts_an_exact_size_body_without_content_length() {
        let url = raw_server(response("200 OK", "", b"four"));
        let text = send_text(client().client.get(url), "test", 4)
            .await
            .unwrap();
        assert_eq!(text, "four");
    }

    #[tokio::test]
    async fn rejects_an_oversized_body_without_content_length() {
        let url = raw_server(response("200 OK", "", b"large"));
        let error = send_text(client().client.get(url), "test", 4)
            .await
            .unwrap_err();
        assert!(matches!(error, ApiError::InvalidResponse { .. }));
    }

    #[tokio::test]
    async fn rejects_a_declared_size_above_the_limit_before_reading() {
        let url = raw_server(response("200 OK", "Content-Length: 100\r\n", b"x"));
        let error = send_text(client().client.get(url), "test", 4)
            .await
            .unwrap_err();
        assert!(matches!(error, ApiError::InvalidResponse { .. }));
    }

    #[tokio::test]
    async fn truncates_large_http_error_details() {
        let body = vec![b'x'; ERROR_RESPONSE_LIMIT as usize + 1024];
        let url = raw_server(response(
            "500 Internal Server Error",
            &format!("Content-Length: {}\r\n", body.len()),
            &body,
        ));
        let error = send_no_content(client().client.get(url)).await.unwrap_err();
        assert!(matches!(
            error,
            ApiError::Http {
                status: 500,
                detail
            } if detail.len() <= ERROR_DETAIL_LIMIT + "…".len()
        ));
    }

    #[tokio::test]
    async fn accepts_an_empty_no_content_response() {
        let url = raw_server(response("204 No Content", "", b""));
        send_no_content(client().client.get(url)).await.unwrap();
    }
}
