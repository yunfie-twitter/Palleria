use super::*;

impl UgoiraMetadataResponse {
    pub(crate) fn into_metadata(self) -> UgoiraMetadata {
        let metadata = self.ugoira_metadata.unwrap_or(UgoiraMetadataDto {
            zip_urls: UgoiraZipUrlsDto::default(),
            frames: Vec::new(),
        });
        UgoiraMetadata {
            zip_url: metadata.zip_urls.medium,
            frames: metadata
                .frames
                .into_iter()
                .map(|frame| UgoiraFrame {
                    file: frame.file,
                    delay_millis: frame.delay,
                })
                .collect(),
        }
    }
}

impl CommentPageResponse {
    pub(crate) fn into_page(self) -> CommentPage {
        CommentPage {
            total_comments: self.total_comments,
            comments: self
                .comments
                .into_iter()
                .map(CommentDto::into_comment)
                .collect(),
            next_url: self.next_url,
        }
    }
}

impl CommentDto {
    fn into_comment(self) -> Comment {
        Comment {
            id: self.id,
            comment: self.comment,
            date: self.date,
            user: self.user.map(CommentUserDto::into_user),
            parent_comment: self.parent_comment.map(|parent| parent.into_parent()),
            has_replies: self.has_replies,
            stamp: self.stamp.map(CommentStampDto::into_stamp),
        }
    }

    fn into_parent(self) -> ParentComment {
        ParentComment {
            id: self.id,
            comment: self.comment,
            date: self.date,
            user: self.user.map(CommentUserDto::into_user),
            has_replies: self.has_replies,
            stamp: self.stamp.map(CommentStampDto::into_stamp),
        }
    }
}

impl CommentUserDto {
    fn into_user(self) -> CommentUser {
        CommentUser {
            id: self.id,
            name: self.name,
            account: self.account,
            profile_image_url: self.profile_image_urls.medium.unwrap_or_default(),
        }
    }
}

impl CommentStampDto {
    fn into_stamp(self) -> CommentStamp {
        CommentStamp {
            stamp_id: self.stamp_id,
            stamp_url: self.stamp_url,
        }
    }
}

impl NotificationPageResponse {
    pub(crate) fn into_page(self) -> NotificationPage {
        NotificationPage {
            notifications: self
                .notifications
                .into_iter()
                .filter_map(NotificationDto::into_notification)
                .collect(),
            next_url: self.next_url,
        }
    }
}

impl NotificationDto {
    fn into_notification(self) -> Option<Notification> {
        Some(Notification {
            id: self.id?,
            created_datetime: self.created_datetime,
            notification_type: self.notification_type,
            content: self.content.map(|content| NotificationContent {
                text: content.text,
                left_icon: content.left_icon,
                left_image: content.left_image,
                right_icon: content.right_icon,
                right_image: content.right_image,
            }),
            view_more: self.view_more.map(|view_more| NotificationViewMore {
                unread_exists: view_more.unread_exists,
                title: view_more.title,
            }),
            target_url: self.target_url,
            is_read: self.is_read,
        })
    }
}

impl SimpleBooleanResponse {
    pub(crate) fn into_value(self) -> OptionalBoolean {
        OptionalBoolean {
            value: self.show_ai,
        }
    }
}

impl StampResponse {
    pub(crate) fn into_list(self) -> StampList {
        StampList {
            items: self
                .stamps
                .into_iter()
                .filter_map(|stamp| {
                    Some(Stamp {
                        id: stamp.stamp_id?,
                        url: stamp.stamp_url?,
                    })
                })
                .collect(),
        }
    }
}

impl TrendingTagResponse {
    pub(crate) fn into_list(self) -> TrendingTagList {
        TrendingTagList {
            items: self
                .trend_tags
                .into_iter()
                .filter_map(|item| {
                    Some(TrendingTag {
                        tag: item.tag?,
                        translated_name: item.translated_name,
                        illust_id: item.illust.as_ref().and_then(|illust| illust.id),
                        thumbnail_url: item
                            .illust
                            .and_then(|illust| illust.image_urls)
                            .and_then(|urls| urls.medium),
                    })
                })
                .collect(),
        }
    }
}

impl IllustBookmarkTagsResponse {
    pub(crate) fn into_list(self) -> BookmarkTagList {
        BookmarkTagList {
            items: self
                .bookmark_tags
                .into_iter()
                .filter(|tag| !tag.name.trim().is_empty())
                .map(|tag| BookmarkTag {
                    name: tag.name,
                    count: tag.count,
                })
                .collect(),
        }
    }
}

impl SpotlightResponse {
    pub(crate) fn into_page(self) -> SpotlightPage {
        SpotlightPage {
            articles: self
                .spotlight_articles
                .into_iter()
                .filter_map(|article| {
                    Some(SpotlightArticle {
                        id: article.id?,
                        title: article.title,
                        pure_title: article.pure_title,
                        thumbnail: article.thumbnail,
                        article_url: article.article_url,
                        publish_date: article.publish_date,
                    })
                })
                .collect(),
            next_url: self.next_url,
        }
    }
}
