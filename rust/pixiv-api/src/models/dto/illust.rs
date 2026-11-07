use super::*;

impl IllustPageResponse {
    pub(crate) fn into_page(self) -> IllustPage {
        IllustPage {
            items: self
                .illusts
                .into_iter()
                .filter_map(IllustDto::into_illust)
                .collect(),
            next_url: self.next_url,
        }
    }
}

impl IllustDetailResponse {
    pub(crate) fn into_illust(self) -> Option<Illust> {
        self.illust.and_then(IllustDto::into_illust)
    }
}

impl IllustDto {
    pub(crate) fn into_illust(self) -> Option<Illust> {
        let id = self.id?;

        // Fill all three page lists in one walk. Each URL variant used to get its own traversal
        // of meta_pages, so every page was visited three times.
        let page_count = self.meta_pages.len();
        let mut medium_pages = Vec::with_capacity(page_count);
        let mut image_pages = Vec::with_capacity(page_count);
        let mut original_pages = Vec::with_capacity(page_count);
        for page in &self.meta_pages {
            let Some(urls) = page.image_urls.as_ref() else {
                continue;
            };
            if let Some(url) = urls.medium.as_ref().or(urls.large.as_ref()) {
                medium_pages.push(url.clone());
            }
            if let Some(url) = urls.large.as_ref().or(urls.medium.as_ref()) {
                image_pages.push(url.clone());
            }
            if let Some(url) = urls.original.as_ref().or(urls.large.as_ref()) {
                original_pages.push(url.clone());
            }
        }

        let original = self
            .meta_single_page
            .as_ref()
            .and_then(|urls| urls.original.as_deref())
            .or_else(|| original_pages.first().map(String::as_str));

        // Same fallback chains as before, but each resolves to a borrowed source and is copied
        // once, rather than cloning every candidate along the way.
        let medium = self
            .image_urls
            .medium
            .as_deref()
            .or(self.image_urls.square_medium.as_deref())
            .or(self.image_urls.large.as_deref())
            .or(original)
            .unwrap_or_default()
            .to_owned();
        let image_url = self
            .image_urls
            .large
            .as_deref()
            .or(self.image_urls.medium.as_deref())
            .or(original)
            .unwrap_or_default()
            .to_owned();

        Some(Illust {
            id,
            title: self.title,
            illust_type: self.illust_type,
            caption: self.caption,
            artist_id: self.user.id,
            artist_name: self.user.name,
            artist_avatar_url: self.user.profile_image_urls.medium,
            square_image_url: self.image_urls.square_medium.unwrap_or_default(),
            medium_image_url: medium,
            image_url,
            original_image_url: original.map(ToOwned::to_owned),
            medium_image_pages: fallback_pages(medium_pages, &image_pages, &original_pages),
            image_pages: fallback_pages(image_pages, &original_pages, &[]),
            original_image_pages: original_pages,
            tags: self.tags.into_iter().filter_map(|tag| tag.name).collect(),
            page_count: self.page_count,
            width: self.width,
            height: self.height,
            is_bookmarked: self.is_bookmarked,
            total_bookmarks: self.total_bookmarks,
            total_comments: self.total_comments,
            series: self.series.map(|series| IllustSeries {
                id: series.id,
                title: series.title,
            }),
            x_restrict: self.x_restrict,
            sanity_level: self.sanity_level,
            illust_ai_type: self.illust_ai_type,
        })
    }
}

fn fallback_pages(primary: Vec<String>, secondary: &[String], tertiary: &[String]) -> Vec<String> {
    if !primary.is_empty() {
        primary
    } else if !secondary.is_empty() {
        secondary.to_vec()
    } else {
        tertiary.to_vec()
    }
}
