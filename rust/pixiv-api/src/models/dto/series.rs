use super::illust::page_urls;
use super::*;

impl IllustSeriesPageResponse {
    pub(crate) fn into_page(self) -> IllustSeriesPage {
        IllustSeriesPage {
            detail: self
                .illust_series_detail
                .and_then(IllustSeriesDetailDto::into_detail),
            first_illust: self
                .illust_series_first_illust
                .and_then(IllustDto::into_series_illust),
            illusts: self
                .illusts
                .into_iter()
                .filter_map(IllustDto::into_series_illust)
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

impl IllustDto {
    fn into_series_illust(self) -> Option<SeriesIllust> {
        let id = self.id?;
        let original_pages = page_urls(&self.meta_pages, |urls| {
            urls.original.as_ref().or(urls.large.as_ref())
        });
        let original = self
            .meta_single_page
            .as_ref()
            .and_then(|urls| urls.original.as_deref())
            .or_else(|| original_pages.first().map(|s| s.as_str()));
        let has_meta_single_page = self.meta_single_page.is_some();
        let medium = self
            .image_urls
            .medium
            .as_deref()
            .or(self.image_urls.square_medium.as_deref())
            .or(self.image_urls.large.as_deref())
            .or(original)
            .unwrap_or_default()
            .to_owned();
        let large = self
            .image_urls
            .large
            .as_deref()
            .or(self.image_urls.medium.as_deref())
            .or(original)
            .unwrap_or_default()
            .to_owned();
        let meta_pages = self
            .meta_pages
            .iter()
            .filter_map(|page| page.image_urls.as_ref())
            .map(|urls| SeriesImagePage {
                square_image_url: urls.square_medium.clone().unwrap_or_default(),
                medium_image_url: urls.medium.clone().unwrap_or_default(),
                large_image_url: urls.large.clone().unwrap_or_default(),
                original_image_url: urls.original.clone().unwrap_or_default(),
            })
            .collect();

        Some(SeriesIllust {
            id,
            title: self.title,
            illust_type: self.illust_type,
            square_image_url: self.image_urls.square_medium.unwrap_or_default(),
            medium_image_url: medium,
            large_image_url: large,
            caption: self.caption,
            restrict: self.restrict,
            user: SeriesIllustUser {
                id: self.user.id,
                name: self.user.name,
                account: self.user.account,
                profile_image_url: self.user.profile_image_urls.medium.unwrap_or_default(),
                comment: self.user.comment,
                is_followed: self.user.is_followed,
            },
            tags: self.tags.into_iter().filter_map(|tag| tag.name).collect(),
            tools: self.tools,
            create_date: self.create_date,
            page_count: self.page_count,
            width: self.width,
            height: self.height,
            sanity_level: self.sanity_level,
            x_restrict: self.x_restrict,
            has_meta_single_page,
            original_image_url: original.map(ToOwned::to_owned),
            meta_pages,
            total_view: self.total_view,
            total_bookmarks: self.total_bookmarks,
            is_bookmarked: self.is_bookmarked,
            visible: self.visible,
            is_muted: self.is_muted,
            illust_ai_type: self.illust_ai_type,
            series: self.series.map(|series| IllustSeries {
                id: series.id,
                title: series.title,
            }),
            illust_book_style: self.illust_book_style,
            total_comments: self.total_comments,
        })
    }
}
