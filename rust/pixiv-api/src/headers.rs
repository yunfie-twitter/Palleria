use chrono::Utc;
use reqwest::header::{
    ACCEPT, ACCEPT_LANGUAGE, AUTHORIZATION, HeaderMap, HeaderName, HeaderValue, REFERER, USER_AGENT,
};

use crate::config::{APP_VERSION, CLIENT_HASH_SECRET};
use crate::error::ApiError;

pub(crate) struct PixivHeaders {
    base: HeaderMap,
}

impl PixivHeaders {
    pub(crate) fn new(
        user_agent: &str,
        app_os_version: &str,
        accept_language: &str,
    ) -> Result<Self, ApiError> {
        let mut base = HeaderMap::with_capacity(9);
        insert(&mut base, USER_AGENT, user_agent)?;
        insert_named(&mut base, "app-os", "Android")?;
        insert_named(&mut base, "app-os-version", app_os_version)?;
        insert_named(&mut base, "app-version", APP_VERSION)?;
        insert(&mut base, ACCEPT_LANGUAGE, accept_language)?;
        Ok(Self { base })
    }

    pub(crate) fn for_request(&self, mut extra: HeaderMap) -> Result<HeaderMap, ApiError> {
        let authenticated = extra.contains_key(AUTHORIZATION);
        for (name, value) in &self.base {
            extra.insert(name, value.clone());
        }
        let client_time = Utc::now().format("%Y-%m-%dT%H:%M:%S+00:00").to_string();
        let client_hash = format!(
            "{:x}",
            md5::compute(format!("{client_time}{CLIENT_HASH_SECRET}"))
        );
        insert_named(&mut extra, "x-client-time", &client_time)?;
        insert_named(&mut extra, "x-client-hash", &client_hash)?;
        if authenticated {
            extra.insert(ACCEPT, HeaderValue::from_static("application/json"));
            extra.insert(REFERER, HeaderValue::from_static("https://www.pixiv.net/"));
        }
        Ok(extra)
    }
}

fn insert(headers: &mut HeaderMap, name: HeaderName, value: &str) -> Result<(), ApiError> {
    let value = HeaderValue::from_str(value).map_err(|error| ApiError::InvalidRequest {
        detail: format!("invalid Pixiv header: {error}"),
    })?;
    headers.insert(name, value);
    Ok(())
}

fn insert_named(headers: &mut HeaderMap, name: &'static str, value: &str) -> Result<(), ApiError> {
    insert(headers, HeaderName::from_static(name), value)
}
