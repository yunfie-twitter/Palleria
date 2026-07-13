use crate::client::PixivHttpClient;
use crate::config::{CLIENT_ID, CLIENT_SECRET, OAUTH_URL, REDIRECT_URI};
use crate::error::{ApiError, http_error, network_error};
use crate::models::LoginSession;

pub(super) fn login_with_refresh_token(
    client: &PixivHttpClient,
    refresh_token: String,
) -> Result<LoginSession, ApiError> {
    let token = refresh_token.trim();
    if token.is_empty() {
        return Err(ApiError::InvalidRequest {
            detail: "refresh token is empty".into(),
        });
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
}

pub(super) fn login_with_authorization_code(
    client: &PixivHttpClient,
    code: String,
    code_verifier: String,
) -> Result<LoginSession, ApiError> {
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

fn oauth_login(
    client: &PixivHttpClient,
    mut fields: Vec<(&str, String)>,
    fallback_refresh_token: Option<&str>,
) -> Result<LoginSession, ApiError> {
    fields.push(("client_id", CLIENT_ID.into()));
    fields.push(("client_secret", CLIENT_SECRET.into()));
    let response = client
        .client
        .post(OAUTH_URL)
        .headers(client.headers.for_request(Default::default())?)
        .form(&fields)
        .send()
        .map_err(network_error)?;
    let status = response.status().as_u16();
    let body = response.text().map_err(network_error)?;
    if !(200..300).contains(&status) {
        return Err(http_error(status, &body));
    }
    parse_session(&body, fallback_refresh_token)
}

fn parse_session(
    body: &str,
    fallback_refresh_token: Option<&str>,
) -> Result<LoginSession, ApiError> {
    let root: serde_json::Value =
        serde_json::from_str(body).map_err(|error| ApiError::InvalidRequest {
            detail: format!("invalid OAuth response: {error}"),
        })?;
    let response = root.get("response").unwrap_or(&root);
    let access_token = response
        .get("access_token")
        .and_then(|value| value.as_str())
        .ok_or_else(|| ApiError::InvalidRequest {
            detail: "OAuth response has no access token".into(),
        })?
        .into();
    let refresh_token = response
        .get("refresh_token")
        .and_then(|value| value.as_str())
        .or(fallback_refresh_token)
        .ok_or_else(|| ApiError::InvalidRequest {
            detail: "OAuth response has no refresh token".into(),
        })?
        .into();
    let user_id = response
        .get("user")
        .and_then(|user| user.get("id"))
        .and_then(|id| id.as_u64().or_else(|| id.as_str()?.parse().ok()));
    Ok(LoginSession {
        access_token,
        refresh_token,
        user_id,
    })
}
