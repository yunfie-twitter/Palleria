#[derive(Clone, Debug, uniffi::Record)]
pub struct UgoiraFrame {
    pub file: String,
    pub delay_millis: i32,
}

#[derive(Debug, uniffi::Record)]
pub struct UgoiraMetadata {
    pub zip_url: String,
    pub frames: Vec<UgoiraFrame>,
}

#[derive(Debug, uniffi::Record)]
pub struct UgoiraPlayback {
    pub frames: Vec<UgoiraFrame>,
}

#[derive(Debug, uniffi::Record)]
pub struct CommentPage {
    pub total_comments: Option<i32>,
    pub comments: Vec<Comment>,
    pub next_url: Option<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct Comment {
    pub id: Option<i64>,
    pub comment: Option<String>,
    pub date: Option<String>,
    pub user: Option<CommentUser>,
    pub parent_comment: Option<ParentComment>,
    pub has_replies: Option<bool>,
    pub stamp: Option<CommentStamp>,
}

#[derive(Debug, uniffi::Record)]
pub struct ParentComment {
    pub id: Option<i64>,
    pub comment: Option<String>,
    pub date: Option<String>,
    pub user: Option<CommentUser>,
    pub has_replies: Option<bool>,
    pub stamp: Option<CommentStamp>,
}

#[derive(Debug, uniffi::Record)]
pub struct CommentUser {
    pub id: Option<i64>,
    pub name: String,
    pub account: String,
    pub profile_image_url: String,
}

#[derive(Debug, uniffi::Record)]
pub struct CommentStamp {
    pub stamp_id: Option<i32>,
    pub stamp_url: Option<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct NotificationPage {
    pub notifications: Vec<Notification>,
    pub next_url: Option<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct Notification {
    pub id: i64,
    pub created_datetime: Option<String>,
    pub notification_type: i32,
    pub content: Option<NotificationContent>,
    pub view_more: Option<NotificationViewMore>,
    pub target_url: Option<String>,
    pub is_read: bool,
}

#[derive(Debug, uniffi::Record)]
pub struct NotificationContent {
    pub text: Option<String>,
    pub left_icon: Option<String>,
    pub left_image: Option<String>,
    pub right_icon: Option<String>,
    pub right_image: Option<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct NotificationViewMore {
    pub unread_exists: bool,
    pub title: Option<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct StampList {
    pub items: Vec<Stamp>,
}

#[derive(Debug, uniffi::Record)]
pub struct Stamp {
    pub id: i64,
    pub url: String,
}

#[derive(Debug, uniffi::Record)]
pub struct TrendingTagList {
    pub items: Vec<TrendingTag>,
}

#[derive(Debug, uniffi::Record)]
pub struct TrendingTag {
    pub tag: String,
    pub translated_name: Option<String>,
    pub illust_id: Option<i64>,
    pub thumbnail_url: Option<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct BookmarkTagList {
    pub items: Vec<BookmarkTag>,
}

#[derive(Debug, uniffi::Record)]
pub struct BookmarkTag {
    pub name: String,
    pub count: i32,
}

#[derive(Debug, uniffi::Record)]
pub struct SpotlightPage {
    pub articles: Vec<SpotlightArticle>,
    pub next_url: Option<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct SpotlightArticle {
    pub id: i64,
    pub title: String,
    pub pure_title: String,
    pub thumbnail: String,
    pub article_url: String,
    pub publish_date: String,
}
