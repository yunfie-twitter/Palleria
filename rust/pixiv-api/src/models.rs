mod dto;

pub(crate) use dto::{
    AccountEditResponse, AutocompleteResponse, CommentPageResponse, IllustBookmarkTagsResponse,
    IllustDetailResponse, IllustPageResponse, IllustSeriesPageResponse, NotificationPageResponse,
    NovelPageResponse, SimpleBooleanResponse, SpotlightResponse, StampResponse,
    TrendingTagResponse, UgoiraMetadataResponse, UserDetailResponse, UserFollowDetailResponse,
    UserMeResponse, UserPreviewPageResponse, WatchlistMangaResponse,
};

mod ffi;
pub use ffi::*;

#[cfg(feature = "bench")]
pub fn benchmark_decode_illust_page(body: &[u8]) -> Result<usize, String> {
    serde_json::from_slice::<IllustPageResponse>(body)
        .map(IllustPageResponse::into_page)
        .map(|page| page.items.len())
        .map_err(|error| error.to_string())
}
