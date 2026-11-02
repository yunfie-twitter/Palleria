mod endpoints;
mod oauth;
mod transport;
mod ugoira_download;

use std::collections::HashMap;
use std::net::SocketAddr;
use std::time::Duration;

use reqwest::Client;

use crate::config::{ECH_HOSTS, ECH_IPS};
use crate::error::{ApiError, invalid_request, invalid_response, network_error};
use crate::headers::PixivHeaders;
use crate::models::{
    AccountEditResponse, AccountEditResult, AutocompleteResponse, BookmarkTagList, CommentPage,
    CommentPageResponse, CurrentUserProfile, Illust, IllustBookmarkTagsResponse,
    IllustDetailResponse, IllustPage, IllustPageResponse, IllustSeriesPage,
    IllustSeriesPageResponse, LoginSession, NotificationPage, NotificationPageResponse, NovelPage,
    NovelPageResponse, NovelText, OptionalBoolean, PixivRequest, SimpleBooleanResponse,
    SpotlightPage, SpotlightResponse, StampList, StampResponse, StringList, TrendingTagList,
    TrendingTagResponse, UgoiraFrame, UgoiraMetadata, UgoiraMetadataResponse, UgoiraPlayback,
    UserDetailResponse, UserFollowDetail, UserFollowDetailResponse, UserMeResponse,
    UserPreviewPage, UserPreviewPageResponse, UserProfile, WatchlistMangaPage,
    WatchlistMangaResponse,
};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum NetworkMode {
    Standard,
    Compat,
    Ech,
}

impl NetworkMode {
    fn parse(value: &str) -> Result<Self, ApiError> {
        match value {
            "standard" => Ok(Self::Standard),
            "compat" => Ok(Self::Compat),
            "ech" => Ok(Self::Ech),
            _ => Err(invalid_request(format!("unknown network mode: {value}"))),
        }
    }
}

#[derive(uniffi::Object)]
pub struct PixivHttpClient {
    pub(super) client: Client,
    pub(super) headers: PixivHeaders,
}

#[uniffi::export]
impl PixivHttpClient {
    #[uniffi::constructor]
    pub fn new(
        network_mode: String,
        user_agent: String,
        app_os_version: String,
        accept_language: String,
    ) -> Result<Self, ApiError> {
        let network_mode = NetworkMode::parse(&network_mode)?;
        let mut builder = Client::builder()
            .connect_timeout(Duration::from_secs(12))
            .timeout(Duration::from_secs(30))
            .pool_max_idle_per_host(6)
            .pool_idle_timeout(Duration::from_secs(300))
            .tcp_keepalive(Duration::from_secs(60));

        if network_mode == NetworkMode::Ech {
            for host in ECH_HOSTS {
                for ip in ECH_IPS {
                    let address: SocketAddr = format!("{ip}:443").parse().map_err(|error| {
                        invalid_request(format!("invalid compatible endpoint: {error}"))
                    })?;
                    builder = builder.resolve(host, address);
                }
            }
        }

        Ok(Self {
            client: builder.build().map_err(network_error)?,
            headers: PixivHeaders::new(&user_agent, &app_os_version, &accept_language)?,
        })
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn login_with_refresh_token(
        &self,
        refresh_token: String,
    ) -> Result<LoginSession, ApiError> {
        oauth::login_with_refresh_token(self, refresh_token).await
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn login_with_authorization_code(
        &self,
        code: String,
        code_verifier: String,
    ) -> Result<LoginSession, ApiError> {
        oauth::login_with_authorization_code(self, code, code_verifier).await
    }

    pub fn create_web_login_url(
        &self,
        create_provisional_account: bool,
        code_challenge: String,
    ) -> String {
        oauth::create_web_login_url(create_provisional_account, code_challenge)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_illust_page(&self, request: PixivRequest) -> Result<IllustPage, ApiError> {
        transport::execute_json::<IllustPageResponse>(self, request, "illust page")
            .await
            .map(IllustPageResponse::into_page)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_illust_detail(&self, request: PixivRequest) -> Result<Illust, ApiError> {
        transport::execute_json::<IllustDetailResponse>(self, request, "illust detail")
            .await?
            .into_illust()
            .ok_or_else(|| {
                invalid_response("illust detail response does not contain a valid illust")
            })
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_user_profile(
        &self,
        request: PixivRequest,
        fallback_user_id: i64,
    ) -> Result<UserProfile, ApiError> {
        transport::execute_json::<UserDetailResponse>(self, request, "user detail")
            .await
            .map(|response| response.into_profile(fallback_user_id))
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_autocomplete(
        &self,
        request: PixivRequest,
    ) -> Result<StringList, ApiError> {
        transport::execute_json::<AutocompleteResponse>(self, request, "autocomplete")
            .await
            .map(AutocompleteResponse::into_list)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_user_preview_page(
        &self,
        request: PixivRequest,
    ) -> Result<UserPreviewPage, ApiError> {
        transport::execute_json::<UserPreviewPageResponse>(self, request, "user preview page")
            .await
            .map(UserPreviewPageResponse::into_page)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_ugoira_metadata(
        &self,
        request: PixivRequest,
    ) -> Result<UgoiraMetadata, ApiError> {
        transport::execute_json::<UgoiraMetadataResponse>(self, request, "ugoira metadata")
            .await
            .map(UgoiraMetadataResponse::into_metadata)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_comment_page(
        &self,
        request: PixivRequest,
    ) -> Result<CommentPage, ApiError> {
        transport::execute_json::<CommentPageResponse>(self, request, "comment page")
            .await
            .map(CommentPageResponse::into_page)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_notification_page(
        &self,
        request: PixivRequest,
    ) -> Result<NotificationPage, ApiError> {
        transport::execute_json::<NotificationPageResponse>(self, request, "notification page")
            .await
            .map(NotificationPageResponse::into_page)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_novel_page(&self, request: PixivRequest) -> Result<NovelPage, ApiError> {
        transport::execute_json::<NovelPageResponse>(self, request, "novel page")
            .await
            .map(NovelPageResponse::into_page)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_watchlist_manga(
        &self,
        request: PixivRequest,
    ) -> Result<WatchlistMangaPage, ApiError> {
        transport::execute_json::<WatchlistMangaResponse>(self, request, "watchlist manga")
            .await
            .map(WatchlistMangaResponse::into_page)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_illust_series_page(
        &self,
        request: PixivRequest,
    ) -> Result<IllustSeriesPage, ApiError> {
        transport::execute_json::<IllustSeriesPageResponse>(self, request, "illust series")
            .await
            .map(IllustSeriesPageResponse::into_page)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_optional_boolean(
        &self,
        request: PixivRequest,
    ) -> Result<OptionalBoolean, ApiError> {
        transport::execute_json::<SimpleBooleanResponse>(self, request, "boolean response")
            .await
            .map(SimpleBooleanResponse::into_value)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_user_follow_detail(
        &self,
        request: PixivRequest,
    ) -> Result<UserFollowDetail, ApiError> {
        transport::execute_json::<UserFollowDetailResponse>(self, request, "user follow detail")
            .await
            .map(UserFollowDetailResponse::into_detail)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_stamps(&self, request: PixivRequest) -> Result<StampList, ApiError> {
        transport::execute_json::<StampResponse>(self, request, "stamps")
            .await
            .map(StampResponse::into_list)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_trending_tags(
        &self,
        request: PixivRequest,
    ) -> Result<TrendingTagList, ApiError> {
        transport::execute_json::<TrendingTagResponse>(self, request, "trending tags")
            .await
            .map(TrendingTagResponse::into_list)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_illust_bookmark_tags(
        &self,
        request: PixivRequest,
    ) -> Result<BookmarkTagList, ApiError> {
        transport::execute_json::<IllustBookmarkTagsResponse>(
            self,
            request,
            "illustration bookmark tags",
        )
        .await
        .map(IllustBookmarkTagsResponse::into_list)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_spotlight(
        &self,
        request: PixivRequest,
    ) -> Result<SpotlightPage, ApiError> {
        transport::execute_json::<SpotlightResponse>(self, request, "spotlight")
            .await
            .map(SpotlightResponse::into_page)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_current_user_profile(
        &self,
        request: PixivRequest,
    ) -> Result<CurrentUserProfile, ApiError> {
        transport::execute_json::<UserMeResponse>(self, request, "current user profile")
            .await?
            .into_profile()
            .ok_or_else(|| {
                invalid_response("current user profile response is missing a valid profile")
            })
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_account_edit(
        &self,
        request: PixivRequest,
    ) -> Result<AccountEditResult, ApiError> {
        transport::execute_json::<AccountEditResponse>(self, request, "account edit")
            .await
            .map(AccountEditResponse::into_result)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_novel_text(
        &self,
        request: PixivRequest,
        novel_id: i64,
    ) -> Result<NovelText, ApiError> {
        let body =
            transport::execute_text(self, request, "novel text", transport::HTML_RESPONSE_LIMIT)
                .await?;
        endpoints::novel_text(&body, novel_id)
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn execute_no_content(&self, request: PixivRequest) -> Result<(), ApiError> {
        transport::execute_no_content(self, request).await
    }

    #[uniffi::method(async_runtime = "tokio")]
    pub async fn prepare_ugoira(
        &self,
        url: String,
        headers: HashMap<String, String>,
        cache_dir: String,
        frames: Vec<UgoiraFrame>,
    ) -> Result<UgoiraPlayback, ApiError> {
        ugoira_download::prepare(self, url, headers, cache_dir, frames).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::thread;

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
    fn rejects_an_unknown_network_mode() {
        let result = PixivHttpClient::new(
            "unknown".into(),
            "test".into(),
            "Android 10".into(),
            "en-US".into(),
        );

        assert!(matches!(
            result,
            Err(ApiError::InvalidRequest { detail }) if detail == "unknown network mode: unknown"
        ));
    }

    fn server(status: u16, body: impl Into<String>) -> String {
        let server = tiny_http::Server::http("127.0.0.1:0").unwrap();
        let address = format!("http://{}", server.server_addr());
        let body = body.into();
        thread::spawn(move || {
            server
                .recv()
                .unwrap()
                .respond(tiny_http::Response::from_string(body).with_status_code(status))
                .unwrap();
        });
        address
    }

    fn request(url: String) -> PixivRequest {
        PixivRequest {
            method: "GET".into(),
            url,
            headers: HashMap::new(),
            body: Vec::new(),
            content_type: None,
        }
    }

    #[tokio::test]
    async fn exposes_http_status_and_body() {
        let error = client()
            .execute_no_content(request(server(403, "denied")))
            .await
            .unwrap_err();
        assert!(matches!(error, ApiError::Http { status: 403, detail } if detail == "denied"));
    }

    #[tokio::test]
    async fn parses_illust_page_before_crossing_the_ffi_boundary() {
        let response = client()
            .execute_illust_page(request(server(
                200,
                r#"{"illusts":[{"id":1}],"next_url":null}"#,
            )))
            .await
            .unwrap();
        assert_eq!(response.items.len(), 1);
        assert_eq!(response.items[0].id, 1);
    }

    #[tokio::test]
    async fn parses_illust_detail_before_crossing_the_ffi_boundary() {
        let response = client()
            .execute_illust_detail(request(server(200, r#"{"illust":{"id":7}}"#)))
            .await
            .unwrap();
        assert_eq!(response.id, 7);
    }

    #[tokio::test]
    async fn parses_user_profile_before_crossing_the_ffi_boundary() {
        let response = client()
            .execute_user_profile(
                request(server(200, r#"{"user":{"name":"artist"},"profile":{}}"#)),
                8,
            )
            .await
            .unwrap();
        assert_eq!(response.id, 8);
        assert_eq!(response.name, "artist");
    }

    #[tokio::test]
    async fn reports_invalid_json_as_an_invalid_response() {
        let error = client()
            .execute_illust_page(request(server(200, "{broken")))
            .await
            .unwrap_err();
        assert!(matches!(error, ApiError::InvalidResponse { .. }));
    }
}
