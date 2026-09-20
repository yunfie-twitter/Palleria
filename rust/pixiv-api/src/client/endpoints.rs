use serde::Deserialize;

use crate::error::{ApiError, invalid_response};
use crate::models::NovelText;

pub(super) fn novel_text(body: &str, novel_id: i64) -> Result<NovelText, ApiError> {
    let trimmed = body.trim();
    let value = if trimmed.starts_with('{') {
        serde_json::from_str::<NovelTextDto>(trimmed)
            .map_err(|error| invalid_response(format!("invalid novel text response: {error}")))?
    } else {
        let marker = body
            .find("novel:")
            .ok_or_else(|| invalid_response(novel_preview_error(body)))?;
        let source = &body[marker + "novel:".len()..];
        serde_json::Deserializer::from_str(source)
            .into_iter::<NovelTextDto>()
            .next()
            .transpose()
            .map_err(|error| invalid_response(format!("invalid novel text response: {error}")))?
            .ok_or_else(|| invalid_response(novel_preview_error(body)))?
    };
    Ok(value.into_novel_text(novel_id))
}

#[derive(Debug, Deserialize)]
struct NovelTextDto {
    #[serde(default)]
    title: String,
    #[serde(default)]
    text: String,
    #[serde(default)]
    novel_text: String,
    #[serde(rename = "seriesNavigation")]
    series_navigation: Option<SeriesNavigationDto>,
}

#[derive(Debug, Deserialize)]
struct SeriesNavigationDto {
    #[serde(rename = "prevNovel")]
    prev_novel: Option<SeriesNovelDto>,
    #[serde(rename = "nextNovel")]
    next_novel: Option<SeriesNovelDto>,
}

#[derive(Debug, Deserialize)]
struct SeriesNovelDto {
    id: Option<i64>,
    title: Option<String>,
}

impl NovelTextDto {
    fn into_novel_text(self, novel_id: i64) -> NovelText {
        let navigation = self.series_navigation;
        NovelText {
            novel_id,
            title: self.title,
            text: if self.text.trim().is_empty() {
                self.novel_text
            } else {
                self.text
            },
            series_prev_id: navigation
                .as_ref()
                .and_then(|value| value.prev_novel.as_ref())
                .and_then(|value| value.id),
            series_prev_title: navigation
                .as_ref()
                .and_then(|value| value.prev_novel.as_ref())
                .and_then(|value| value.title.clone()),
            series_next_id: navigation
                .as_ref()
                .and_then(|value| value.next_novel.as_ref())
                .and_then(|value| value.id),
            series_next_title: navigation
                .and_then(|value| value.next_novel)
                .and_then(|value| value.title),
        }
    }
}

fn novel_preview_error(body: &str) -> String {
    let mut preview = String::new();
    for line in body.lines().take(8) {
        if !preview.is_empty() {
            preview.push(' ');
        }
        preview.push_str(line.trim());
        if preview.len() >= 320 {
            break;
        }
    }
    format!(
        "novel response could not be parsed: {}",
        preview.chars().take(320).collect::<String>()
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_direct_novel_json() {
        let result = novel_text(
            r#"{
                "title":"Novel",
                "text":"Body",
                "seriesNavigation":{
                    "prevNovel":{"id":1,"title":"Previous"},
                    "nextNovel":{"id":3,"title":"Next"}
                }
            }"#,
            2,
        )
        .unwrap();

        assert_eq!(result.novel_id, 2);
        assert_eq!(result.text, "Body");
        assert_eq!(result.series_prev_id, Some(1));
        assert_eq!(result.series_next_title.as_deref(), Some("Next"));
    }

    #[test]
    fn extracts_novel_json_from_the_webview_payload() {
        let result = novel_text(
            r#"<script>window.__DATA__ = { novel: {"title":"Novel","novel_text":"Body"}, isOwnWork: false };</script>"#,
            4,
        )
        .unwrap();

        assert_eq!(result.title, "Novel");
        assert_eq!(result.text, "Body");
    }

    #[test]
    fn rejects_a_webview_payload_without_novel_data() {
        assert!(novel_text("<html>missing</html>", 1).is_err());
    }
}
