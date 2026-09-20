mod client;
mod config;
mod error;
mod headers;
mod image_analysis;
mod models;
mod temp_path;
mod ugoira;

pub use client::PixivHttpClient;
pub use error::{ApiError, ApiException};
pub use image_analysis::{ImageAnalysis, analyze_rgba};
#[cfg(feature = "bench")]
pub use models::benchmark_decode_illust_page;
pub use models::{
    AccountEditResult, Comment, CommentPage, CommentStamp, CommentUser, CurrentUserProfile, Illust,
    IllustPage, IllustSeries, IllustSeriesDetail, IllustSeriesPage, LoginSession, MangaSeries,
    MangaSeriesUser, Notification, NotificationContent, NotificationPage, NotificationViewMore,
    NovelPage, NovelPreview, NovelText, OptionalBoolean, ParentComment, PixivRequest,
    SeriesUser, SpotlightArticle, SpotlightPage, Stamp, StampList, StringList, TrendingTag,
    TrendingTagList, UgoiraFrame, UgoiraMetadata, UgoiraPlayback, UserFollowDetail, UserPreview,
    UserPreviewPage, UserProfile, WatchlistMangaPage,
};

uniffi::setup_scaffolding!();
