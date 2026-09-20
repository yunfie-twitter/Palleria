impl IllustSeriesPageResponse {
    pub(crate) fn into_page(self) -> IllustSeriesPage {
        IllustSeriesPage {
            detail: self
                .illust_series_detail
                .and_then(IllustSeriesDetailDto::into_detail),
            first_illust: self
                .illust_series_first_illust
                .and_then(IllustDto::into_illust),
            illusts: self
                .illusts
                .into_iter()
                .filter_map(IllustDto::into_illust)
                .collect(),
            next_url: self.next_url,
        }
    }
}

impl IllustSeriesDetailDto {
    fn into_detail(self) -> Option<IllustSeriesDetail> {
        Some(IllustSeriesDetail {
            height: self.height,
            series_work_count: self.series_work_count,
            id: self.id?,
            create_date: self.create_date,
            title: self.title,
            width: self.width,
            cover_image_url: self.cover_image_urls.and_then(|urls| urls.medium),
            watchlist_added: self.watchlist_added,
            caption: self.caption,
            user: self.user.and_then(SeriesUserDto::into_user),
        })
    }
}

impl SeriesUserDto {
    fn into_user(self) -> Option<SeriesUser> {
        Some(SeriesUser {
            id: self.id?,
            account: self.account,
            name: self.name,
            profile_image_url: self.profile_image_urls.and_then(|urls| urls.medium),
            is_followed: self.is_followed,
        })
    }
}
