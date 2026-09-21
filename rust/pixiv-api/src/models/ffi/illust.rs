#[derive(Debug, uniffi::Record)]
pub struct IllustPage {
    pub items: Vec<Illust>,
    pub next_url: Option<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct Illust {
    pub id: i64,
    pub title: String,
    pub illust_type: String,
    pub caption: String,
    pub artist_id: i64,
    pub artist_name: String,
    pub artist_avatar_url: Option<String>,
    pub square_image_url: String,
    pub medium_image_url: String,
    pub image_url: String,
    pub original_image_url: Option<String>,
    pub medium_image_pages: Vec<String>,
    pub image_pages: Vec<String>,
    pub original_image_pages: Vec<String>,
    pub tags: Vec<String>,
    pub page_count: i32,
    pub is_bookmarked: bool,
    pub total_bookmarks: i32,
    pub total_comments: Option<i32>,
    pub series: Option<IllustSeries>,
    pub x_restrict: i32,
    pub sanity_level: i32,
    pub illust_ai_type: i32,
}

#[derive(Debug, uniffi::Record)]
pub struct IllustSeries {
    pub id: i64,
    pub title: Option<String>,
}
