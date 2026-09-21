use super::*;

#[test]
fn parses_illust_page_and_applies_kotlin_compatible_fallbacks() {
    let response: IllustPageResponse = serde_json::from_str(
            r#"{
                "illusts": [{
                    "id": 42,
                    "title": "sample",
                    "type": "manga",
                    "user": {
                        "id": 7,
                        "name": "artist",
                        "profile_image_urls": {"medium": "avatar"}
                    },
                    "image_urls": {"square_medium": "square", "large": "large"},
                    "meta_pages": [{
                        "image_urls": {"medium": "page-medium", "large": "page-large", "original": "page-original"}
                    }],
                    "tags": [{"name": "tag"}],
                    "page_count": 1,
                    "is_bookmarked": true,
                    "total_bookmarks": 123,
                    "x_restrict": 1,
                    "sanity_level": 4,
                    "illust_ai_type": 2
                }],
                "next_url": "https://example.test/next"
            }"#,
        )
        .unwrap();

    let page = response.into_page();
    assert_eq!(page.next_url.as_deref(), Some("https://example.test/next"));
    assert_eq!(page.items.len(), 1);
    let illust = &page.items[0];
    assert_eq!(illust.id, 42);
    assert_eq!(illust.medium_image_url, "square");
    assert_eq!(illust.original_image_url.as_deref(), Some("page-original"));
    assert_eq!(illust.image_pages, ["page-large"]);
    assert_eq!(illust.tags, ["tag"]);
    assert!(illust.is_bookmarked);
    assert_eq!(illust.total_bookmarks, 123);
    assert_eq!(illust.x_restrict, 1);
    assert_eq!(illust.sanity_level, 4);
    assert_eq!(illust.illust_ai_type, 2);
}

#[test]
fn skips_an_illust_without_an_id_like_the_kotlin_mapper() {
    let response =
        serde_json::from_str::<IllustPageResponse>(r#"{"illusts":[{"title":"missing id"}]}"#)
            .unwrap();
    assert!(response.into_page().items.is_empty());
}

#[test]
fn parses_an_illust_detail() {
    let response: IllustDetailResponse =
        serde_json::from_str(r#"{"illust":{"id":42,"title":"detail"}}"#).unwrap();
    let illust = response.into_illust().unwrap();
    assert_eq!(illust.id, 42);
    assert_eq!(illust.title, "detail");
}

#[test]
fn parses_a_user_detail_and_uses_the_requested_id_as_fallback() {
    let response: UserDetailResponse = serde_json::from_str(
        r#"{
                "user": {
                    "name": "artist",
                    "account": "artist-account",
                    "profile_image_urls": {"medium": "avatar"},
                    "is_followed": true
                },
                "profile": {
                    "background_image_url": "background",
                    "comment": "hello"
                }
            }"#,
    )
    .unwrap();
    let profile = response.into_profile(99);
    assert_eq!(profile.id, 99);
    assert_eq!(profile.account, "artist-account");
    assert_eq!(profile.profile_image_url.as_deref(), Some("avatar"));
    assert!(profile.is_followed);
}

#[test]
fn parses_user_previews_and_nested_illusts() {
    let response: UserPreviewPageResponse = serde_json::from_str(
        r#"{
                "user_previews": [{
                    "user": {
                        "id": 7,
                        "name": "artist",
                        "account": "artist-account",
                        "comment": "hello",
                        "profile_image_urls": {"medium": "avatar"},
                        "is_followed": true
                    },
                    "illusts": [{"id": 42, "title": "preview"}]
                }],
                "next_url": "https://example.test/next"
            }"#,
    )
    .unwrap();

    let page = response.into_page();
    assert_eq!(page.items.len(), 1);
    assert_eq!(page.items[0].comment, "hello");
    assert_eq!(page.items[0].preview_illusts[0].id, 42);
    assert_eq!(page.next_url.as_deref(), Some("https://example.test/next"));
}

#[test]
fn parses_ugoira_metadata_and_preserves_delays() {
    let response: UgoiraMetadataResponse = serde_json::from_str(
        r#"{
                "ugoira_metadata": {
                    "zip_urls": {"medium": "https://example.test/ugoira.zip"},
                    "frames": [{"file": "000000.jpg", "delay": 80}]
                }
            }"#,
    )
    .unwrap();

    let metadata = response.into_metadata();
    assert_eq!(metadata.zip_url, "https://example.test/ugoira.zip");
    assert_eq!(metadata.frames[0].file, "000000.jpg");
    assert_eq!(metadata.frames[0].delay_millis, 80);
}

#[test]
fn parses_comments_with_a_parent_without_exposing_recursive_ffi_records() {
    let response: CommentPageResponse = serde_json::from_str(
        r#"{
                "total_comments": 2,
                "comments": [{
                    "id": 2,
                    "comment": "reply",
                    "parent_comment": {
                        "id": 1,
                        "comment": "parent",
                        "user": {
                            "id": 9,
                            "name": "artist",
                            "account": "artist-account",
                            "profile_image_urls": {"medium": "avatar"}
                        }
                    }
                }]
            }"#,
    )
    .unwrap();

    let page = response.into_page();
    assert_eq!(page.total_comments, Some(2));
    assert_eq!(
        page.comments[0]
            .parent_comment
            .as_ref()
            .and_then(|comment| comment.id),
        Some(1)
    );
}

#[test]
fn skips_notifications_and_novels_without_ids() {
    let notifications: NotificationPageResponse =
        serde_json::from_str(r#"{"notifications":[{"type":1},{"id":4,"type":2}]}"#).unwrap();
    assert_eq!(notifications.into_page().notifications.len(), 1);

    let novels: NovelPageResponse =
        serde_json::from_str(r#"{"novels":[{"title":"missing"},{"id":8,"title":"kept"}]}"#)
            .unwrap();
    assert_eq!(novels.into_page().items[0].id, 8);
}

#[test]
fn parses_watchlist_series_and_optional_user_data() {
    let response: WatchlistMangaResponse = serde_json::from_str(
        r#"{
                "series": [{
                    "id": 12,
                    "title": "series",
                    "published_content_count": 3,
                    "latest_content_id": 99,
                    "user": {
                        "id": 5,
                        "name": "artist",
                        "profile_image_urls": {"medium": "avatar"}
                    }
                }]
            }"#,
    )
    .unwrap();

    let page = response.into_page();
    assert_eq!(page.series[0].id, 12);
    assert_eq!(
        page.series[0]
            .user
            .as_ref()
            .and_then(|user| user.profile_image_url.as_deref()),
        Some("avatar")
    );
}

#[test]
fn parses_full_illust_series_without_dropping_series_fields() {
    let response: IllustSeriesPageResponse = serde_json::from_str(
        r#"{
                "illust_series_detail": {
                    "id": 2,
                    "title": "series",
                    "series_work_count": 1,
                    "cover_image_urls": {"medium": "cover"},
                    "user": {"id": 3, "name": "artist"}
                },
                "illust_series_first_illust": {
                    "id": 4,
                    "title": "work",
                    "image_urls": {"medium": "medium", "large": "large"},
                    "meta_single_page": {"original_image_url": "original"},
                    "tools": ["tool"],
                    "total_view": 10,
                    "total_bookmarks": 5,
                    "visible": true
                },
                "illusts": []
            }"#,
    )
    .unwrap();

    let page = response.into_page();
    assert_eq!(page.detail.as_ref().map(|detail| detail.id), Some(2));
    let first = page.first_illust.unwrap();
    assert_eq!(first.original_image_url.as_deref(), Some("original"));
    assert_eq!(first.total_bookmarks, 5);
}
