use serde::Deserialize;

use crate::client::PixivHttpClient;
use crate::client::transport;
use crate::config::{CLIENT_ID, CLIENT_SECRET, OAUTH_URL, REDIRECT_URI};
use crate::error::{ApiError, invalid_request, invalid_response};
use crate::models::LoginSession;

pub(super) async fn login_with_refresh_token(
    client: &PixivHttpClient,
    refresh_token: String,
) -> Result<LoginSession, ApiError> {
    let token = refresh_token.trim();
    if token.is_empty() {
        return Err(invalid_request("refresh token is empty"));
    }
    oauth_login(
        client,
        vec![
            ("grant_type", "refresh_token".into()),
            ("include_policy", "true".into()),
            ("refresh_token", token.into()),
        ],
        Some(token),
    )
    .await
}

pub(super) async fn login_with_authorization_code(
    client: &PixivHttpClient,
    code: String,
    code_verifier: String,
) -> Result<LoginSession, ApiError> {
    if code.trim().is_empty() {
        return Err(invalid_request("authorization code is empty"));
    }
    if code_verifier.trim().is_empty() {
        return Err(invalid_request("authorization code verifier is empty"));
    }
    oauth_login(
        client,
        vec![
            ("grant_type", "authorization_code".into()),
            ("include_policy", "true".into()),
            ("code", code),
            ("code_verifier", code_verifier),
            ("redirect_uri", REDIRECT_URI.into()),
        ],
        None,
    )
    .await
}

pub(super) fn create_web_login_url(
    create_provisional_account: bool,
    code_challenge: String,
) -> String {
    let path = if create_provisional_account {
        "provisional-accounts/create"
    } else {
        "login"
    };
    reqwest::Url::parse(&format!("https://app-api.pixiv.net/web/v1/{path}"))
        .expect("static Pixiv login URL must be valid")
        .query_pairs_mut()
        .append_pair("code_challenge", &code_challenge)
        .append_pair("code_challenge_method", "S256")
        .append_pair("client", "pixiv-android")
        .finish()
        .to_string()
}

async fn oauth_login(
    client: &PixivHttpClient,
    mut fields: Vec<(&str, String)>,
    fallback_refresh_token: Option<&str>,
) -> Result<LoginSession, ApiError> {
    fields.push(("client_id", CLIENT_ID.into()));
    fields.push(("client_secret", CLIENT_SECRET.into()));
    let payload = transport::send_json::<OAuthPayload>(
        client
            .client
            .post(OAUTH_URL)
            .headers(client.headers.for_request(Default::default())?)
            .form(&fields),
        "OAuth",
    )
    .await?;
    finish_session(payload, fallback_refresh_token)
}

#[cfg(test)]
fn parse_session(
    body: &str,
    fallback_refresh_token: Option<&str>,
) -> Result<LoginSession, ApiError> {
    let payload: OAuthPayload = serde_json::from_str(body)
        .map_err(|error| invalid_response(format!("invalid OAuth response: {error}")))?;
    finish_session(payload, fallback_refresh_token)
}

fn finish_session(
    payload: OAuthPayload,
    fallback_refresh_token: Option<&str>,
) -> Result<LoginSession, ApiError> {
    let response = payload.into_response();
    let refresh_token = response
        .refresh_token
        .or_else(|| fallback_refresh_token.map(str::to_owned))
        .ok_or_else(|| invalid_response("OAuth response has no refresh token"))?;
    Ok(LoginSession {
        expires_in: response.expires_in,
        access_token: response.access_token,
        refresh_token,
        user_id: response.user.and_then(|user| user.id?.into_u64()),
    })
}

#[derive(Debug, Deserialize)]
#[serde(untagged)]
enum OAuthPayload {
    Wrapped { response: OAuthResponse },
    Direct(OAuthResponse),
}

impl OAuthPayload {
    fn into_response(self) -> OAuthResponse {
        match self {
            Self::Wrapped { response } | Self::Direct(response) => response,
        }
    }
}

#[derive(Debug, Deserialize)]
struct OAuthResponse {
    expires_in: Option<u64>,
    access_token: String,
    refresh_token: Option<String>,
    user: Option<OAuthUser>,
}

#[derive(Debug, Deserialize)]
struct OAuthUser {
    id: Option<OAuthUserId>,
}

#[derive(Debug, Deserialize)]
#[serde(untagged)]
enum OAuthUserId {
    Integer(u64),
    Text(String),
}

impl OAuthUserId {
    fn into_u64(self) -> Option<u64> {
        match self {
            Self::Integer(id) => Some(id),
            Self::Text(id) => id.parse().ok(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_wrapped_oauth_response_with_string_user_id() {
        let session = parse_session(
            r#"{"response":{"access_token":"access","refresh_token":"refresh","expires_in":3600,"user":{"id":"42"}}}"#,
            None,
        )
        .unwrap();

        assert_eq!(session.access_token, "access");
        assert_eq!(session.refresh_token, "refresh");
        assert_eq!(session.user_id, Some(42));
        assert_eq!(session.expires_in, Some(3600));
    }

    #[test]
    fn uses_fallback_refresh_token_for_direct_response() {
        let session = parse_session(r#"{"access_token":"access"}"#, Some("fallback")).unwrap();

        assert_eq!(session.refresh_token, "fallback");
        assert_eq!(session.expires_in, None);
    }
}
