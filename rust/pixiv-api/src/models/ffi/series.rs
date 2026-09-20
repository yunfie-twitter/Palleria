use super::Illust;

#[derive(Debug, uniffi::Record)]
pub struct IllustSeriesPage {
    pub detail: Option<IllustSeriesDetail>,
    pub first_illust: Option<Illust>,
    pub illusts: Vec<Illust>,
    pub next_url: Option<String>,
}

#[derive(Debug, uniffi::Record)]
pub struct IllustSeriesDetail {
    pub height: i32,
    pub series_work_count: i32,
    pub id: i64,
    pub create_date: String,
    pub title: String,
    pub width: i32,
    pub cover_image_url: Option<String>,
    pub watchlist_added: bool,
    pub caption: String,
    pub user: Option<SeriesUser>,
}

#[derive(Debug, uniffi::Record)]
pub struct SeriesUser {
    pub id: i64,
    pub account: String,
    pub name: String,
    pub profile_image_url: Option<String>,
    pub is_followed: bool,
}
