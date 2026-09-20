use std::collections::HashMap;

use serde::Deserialize;

use super::{
    AccountEditResult, Comment, CommentPage, CommentStamp, CommentUser, CurrentUserProfile, Illust,
    IllustPage, IllustSeries, IllustSeriesDetail, IllustSeriesPage, MangaSeries, MangaSeriesUser,
    Notification, NotificationContent, NotificationPage, NotificationViewMore, NovelPage,
    NovelPreview, OptionalBoolean, ParentComment, SeriesUser, SpotlightArticle, SpotlightPage,
    Stamp, StampList, StringList, TrendingTag, TrendingTagList, UgoiraFrame, UgoiraMetadata,
    UserFollowDetail, UserPreview, UserPreviewPage, UserProfile, WatchlistMangaPage,
};

#[derive(Debug, Deserialize)]
pub(crate) struct IllustPageResponse {
    #[serde(default)]
    illusts: Vec<IllustDto>,
    next_url: Option<String>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct IllustDetailResponse {
    illust: Option<IllustDto>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct UserDetailResponse {
    #[serde(default)]
    user: UserDto,
    profile: Option<ProfileDto>,
}

#[derive(Debug, Default, Deserialize)]
pub(crate) struct ImageUrls {
    square_medium: Option<String>,
    medium: Option<String>,
    large: Option<String>,
    #[serde(alias = "original_image_url")]
    original: Option<String>,
}

#[derive(Debug, Default, Deserialize)]
pub(crate) struct UserDto {
    #[serde(default)]
    id: i64,
    #[serde(default)]
    name: String,
    #[serde(default)]
    account: String,
    comment: Option<String>,
    #[serde(default)]
    profile_image_urls: ImageUrls,
    is_followed: Option<bool>,
}

#[derive(Debug, Default, Deserialize)]
struct ProfileDto {
    background_image_url: Option<String>,
    #[serde(default)]
    comment: String,
}

#[derive(Debug, Deserialize)]
struct MetaPage {
    image_urls: Option<ImageUrls>,
}

#[derive(Debug, Default, Deserialize)]
struct TagDto {
    name: Option<String>,
}

#[derive(Debug, Deserialize)]
struct SeriesDto {
    #[serde(default)]
    id: i64,
    title: Option<String>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct IllustDto {
    id: Option<i64>,
    #[serde(default)]
    title: String,
    #[serde(rename = "type", default = "default_illust_type")]
    illust_type: String,
    #[serde(default)]
    caption: String,
    #[serde(default)]
    user: UserDto,
    #[serde(default)]
    image_urls: ImageUrls,
    meta_single_page: Option<ImageUrls>,
    #[serde(default)]
    meta_pages: Vec<MetaPage>,
    #[serde(default)]
    tags: Vec<TagDto>,
    #[serde(default = "default_page_count")]
    page_count: i32,
    #[serde(default)]
    is_bookmarked: bool,
    total_comments: Option<i32>,
    series: Option<SeriesDto>,
    #[serde(default)]
    total_bookmarks: i32,
}

#[derive(Debug, Deserialize)]
pub(crate) struct AutocompleteResponse {
    #[serde(default)]
    tags: Vec<AutocompleteTagDto>,
}

#[derive(Debug, Deserialize)]
struct AutocompleteTagDto {
    name: Option<String>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct UserPreviewPageResponse {
    #[serde(default)]
    user_previews: Vec<UserPreviewDto>,
    next_url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct UserPreviewDto {
    user: Option<UserDto>,
    #[serde(default)]
    illusts: Vec<IllustDto>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct UgoiraMetadataResponse {
    ugoira_metadata: Option<UgoiraMetadataDto>,
}

#[derive(Debug, Deserialize)]
struct UgoiraMetadataDto {
    #[serde(default)]
    zip_urls: UgoiraZipUrlsDto,
    #[serde(default)]
    frames: Vec<UgoiraFrameDto>,
}

#[derive(Debug, Default, Deserialize)]
struct UgoiraZipUrlsDto {
    #[serde(default)]
    medium: String,
}

#[derive(Debug, Deserialize)]
struct UgoiraFrameDto {
    #[serde(default)]
    file: String,
    #[serde(default)]
    delay: i32,
}

#[derive(Debug, Deserialize)]
pub(crate) struct CommentPageResponse {
    total_comments: Option<i32>,
    #[serde(default)]
    comments: Vec<CommentDto>,
    next_url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct CommentDto {
    id: Option<i64>,
    comment: Option<String>,
    date: Option<String>,
    user: Option<CommentUserDto>,
    parent_comment: Option<Box<CommentDto>>,
    has_replies: Option<bool>,
    stamp: Option<CommentStampDto>,
}

#[derive(Debug, Deserialize)]
struct CommentUserDto {
    id: Option<i64>,
    #[serde(default)]
    name: String,
    #[serde(default)]
    account: String,
    #[serde(default)]
    profile_image_urls: ImageUrls,
}

#[derive(Debug, Deserialize)]
struct CommentStampDto {
    stamp_id: Option<i32>,
    stamp_url: Option<String>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct NotificationPageResponse {
    #[serde(default)]
    notifications: Vec<NotificationDto>,
    next_url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct NotificationDto {
    id: Option<i64>,
    created_datetime: Option<String>,
    #[serde(rename = "type", default)]
    notification_type: i32,
    content: Option<NotificationContentDto>,
    view_more: Option<NotificationViewMoreDto>,
    target_url: Option<String>,
    #[serde(default = "default_true")]
    is_read: bool,
}

#[derive(Debug, Deserialize)]
struct NotificationContentDto {
    text: Option<String>,
    left_icon: Option<String>,
    left_image: Option<String>,
    right_icon: Option<String>,
    right_image: Option<String>,
}

#[derive(Debug, Deserialize)]
struct NotificationViewMoreDto {
    #[serde(default)]
    unread_exists: bool,
    title: Option<String>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct NovelPageResponse {
    #[serde(default)]
    novels: Vec<NovelDto>,
    next_url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct NovelDto {
    id: Option<i64>,
    #[serde(default)]
    title: String,
    #[serde(default)]
    caption: String,
    #[serde(default)]
    user: UserDto,
    #[serde(default)]
    image_urls: ImageUrls,
    #[serde(default)]
    page_count: i32,
    #[serde(default)]
    text_length: i32,
    #[serde(default)]
    is_bookmarked: bool,
    #[serde(default)]
    total_bookmarks: i32,
    #[serde(default)]
    total_view: i32,
}

#[derive(Debug, Deserialize)]
pub(crate) struct WatchlistMangaResponse {
    #[serde(default)]
    series: Vec<MangaSeriesDto>,
    next_url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct MangaSeriesDto {
    id: Option<i64>,
    url: Option<String>,
    #[serde(default)]
    published_content_count: i32,
    #[serde(default)]
    title: String,
    user: Option<MangaSeriesUserDto>,
    last_published_content_datetime: Option<String>,
    #[serde(default)]
    latest_content_id: i64,
    thumbnail_url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct MangaSeriesUserDto {
    #[serde(default)]
    id: i64,
    #[serde(default)]
    name: String,
    account: Option<String>,
    profile_image_urls: Option<ImageUrls>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct SimpleBooleanResponse {
    show_ai: Option<bool>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct UserFollowDetailResponse {
    is_followed: Option<bool>,
    is_follow: Option<bool>,
    restrict: Option<String>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct StampResponse {
    #[serde(default)]
    stamps: Vec<StampDto>,
}

#[derive(Debug, Deserialize)]
struct StampDto {
    stamp_id: Option<i64>,
    stamp_url: Option<String>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct TrendingTagResponse {
    #[serde(default)]
    trend_tags: Vec<TrendingTagDto>,
}

#[derive(Debug, Deserialize)]
struct TrendingTagDto {
    tag: Option<String>,
    translated_name: Option<String>,
    illust: Option<TrendingIllustDto>,
}

#[derive(Debug, Deserialize)]
struct TrendingIllustDto {
    id: Option<i64>,
    image_urls: Option<ImageUrls>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct SpotlightResponse {
    #[serde(default)]
    spotlight_articles: Vec<SpotlightArticleDto>,
    next_url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct SpotlightArticleDto {
    id: Option<i64>,
    #[serde(default)]
    title: String,
    #[serde(default)]
    pure_title: String,
    #[serde(default)]
    thumbnail: String,
    #[serde(default)]
    article_url: String,
    #[serde(default)]
    publish_date: String,
}

#[derive(Debug, Deserialize)]
pub(crate) struct UserMeResponse {
    profile: Option<UserMeProfileDto>,
}

#[derive(Debug, Deserialize)]
struct UserMeProfileDto {
    user_id: Option<i64>,
    #[serde(default)]
    pixiv_id: String,
    #[serde(default)]
    name: String,
    profile_image_urls: Option<ImageUrls>,
    #[serde(default)]
    is_premium: bool,
    #[serde(default)]
    x_restrict: i32,
}

#[derive(Debug, Deserialize)]
pub(crate) struct AccountEditResponse {
    body: Option<AccountEditBodyDto>,
    #[serde(default)]
    error: bool,
    #[serde(default)]
    message: String,
}

#[derive(Debug, Deserialize)]
struct AccountEditBodyDto {
    is_succeed: Option<bool>,
    #[serde(default)]
    validation_errors: HashMap<String, serde_json::Value>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct IllustSeriesPageResponse {
    illust_series_detail: Option<IllustSeriesDetailDto>,
    illust_series_first_illust: Option<IllustDto>,
    #[serde(default)]
    illusts: Vec<IllustDto>,
    next_url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct IllustSeriesDetailDto {
    #[serde(default)]
    height: i32,
    #[serde(default)]
    series_work_count: i32,
    id: Option<i64>,
    #[serde(default)]
    create_date: String,
    #[serde(default)]
    title: String,
    #[serde(default)]
    width: i32,
    cover_image_urls: Option<ImageUrls>,
    #[serde(default)]
    watchlist_added: bool,
    #[serde(default)]
    caption: String,
    user: Option<SeriesUserDto>,
}

#[derive(Debug, Deserialize)]
struct SeriesUserDto {
    id: Option<i64>,
    #[serde(default)]
    account: String,
    #[serde(default)]
    name: String,
    profile_image_urls: Option<ImageUrls>,
    #[serde(default)]
    is_followed: bool,
}

fn default_illust_type() -> String {
    "illust".into()
}

fn default_page_count() -> i32 {
    1
}

fn default_true() -> bool {
    true
}

mod activity;
mod illust;
mod novel;
mod series;
mod user;

#[cfg(test)]
mod tests;
