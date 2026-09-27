use std::collections::HashSet;
use std::fs::{self, File};
use std::io::{self, Read};
use std::path::{Component, Path};

use zip::ZipArchive;

use crate::error::{ApiError, invalid_request, io_error};
use crate::models::{UgoiraFrame, UgoiraPlayback};
use crate::temp_path::TempPath;

const MAX_ENTRIES: usize = 10_000;
const MAX_ENTRY_BYTES: u64 = 32 * 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 512 * 1024 * 1024;
const COMPLETE_MARKER: &str = ".complete";
const MIN_VALID_FRAME_DELAY_MILLIS: i32 = 20;
const DEFAULT_FRAME_DELAY_MILLIS: i32 = 100;

pub(crate) fn cached(cache_dir: &Path, frames: &[UgoiraFrame]) -> Option<UgoiraPlayback> {
    validate_frame_names(frames).ok()?;
    cache_is_complete(cache_dir, frames).then(|| playback(cache_dir, frames.to_vec()))
}

pub(crate) async fn prepare(
    zip_path: &Path,
    cache_dir: &Path,
    frames: Vec<UgoiraFrame>,
) -> Result<UgoiraPlayback, ApiError> {
    validate_frame_names(&frames)?;
    if cache_is_complete(cache_dir, &frames) {
        return Ok(playback(cache_dir, frames));
    }

    let parent = cache_dir
        .parent()
        .ok_or_else(|| invalid("ugoira cache directory has no parent"))?;
    fs::create_dir_all(parent).map_err(|error| io_error("create ugoira cache parent", error))?;
    let staging = TempPath::sibling(cache_dir, "staging");
    fs::create_dir_all(staging.path())
        .map_err(|error| io_error("create ugoira staging directory", error))?;

    let staging = extract_required(zip_path, staging, &frames).await?;
    File::create(staging.path().join(COMPLETE_MARKER))
        .map_err(|error| io_error("create ugoira completion marker", error))?;
    remove_if_exists(cache_dir)?;
    fs::rename(staging.path(), cache_dir)
        .map_err(|error| io_error("publish ugoira cache", error))?;

    Ok(playback(cache_dir, frames))
}

async fn extract_required(
    zip_path: &Path,
    staging: TempPath,
    frames: &[UgoiraFrame],
) -> Result<TempPath, ApiError> {
    let zip_path = zip_path.to_owned();
    let frames = frames.to_vec();

    // Keep the staging guard inside the worker so cancellation cannot remove it
    // while extraction is still writing files.
    tokio::task::spawn_blocking(move || {
        let file = File::open(&zip_path).map_err(|error| io_error("open ugoira archive", error))?;
        let mut archive = ZipArchive::new(file).map_err(zip_error)?;
        if archive.len() > MAX_ENTRIES {
            return Err(invalid("ugoira archive contains too many entries"));
        }

        let required: HashSet<&str> = frames.iter().map(|frame| frame.file.as_str()).collect();
        let mut extracted = HashSet::with_capacity(required.len());
        let mut total_bytes = 0_u64;

        for index in 0..archive.len() {
            let mut entry = archive.by_index(index).map_err(zip_error)?;
            let name = entry.name().replace('\\', "/");
            validate_relative_path(&name)?;
            if entry
                .unix_mode()
                .is_some_and(|mode| mode & 0o170000 == 0o120000)
            {
                return Err(invalid("ugoira archive contains a symbolic link"));
            }
            if entry.is_dir() || !required.contains(name.as_str()) {
                continue;
            }
            if !extracted.insert(name.clone()) {
                return Err(invalid("ugoira archive contains duplicate frame entries"));
            }
            if entry.size() > MAX_ENTRY_BYTES {
                return Err(invalid("ugoira frame exceeds the size limit"));
            }
            total_bytes = total_bytes
                .checked_add(entry.size())
                .ok_or_else(|| invalid("ugoira archive size overflow"))?;
            if total_bytes > MAX_TOTAL_BYTES {
                return Err(invalid("ugoira archive exceeds the total size limit"));
            }

            let target = staging.path().join(&name);
            if let Some(parent) = target.parent() {
                fs::create_dir_all(parent)
                    .map_err(|error| io_error("create ugoira frame directory", error))?;
            }
            let mut output =
                File::create(&target).map_err(|error| io_error("create ugoira frame", error))?;
            let expected_size = entry.size();
            let copied = io::copy(&mut entry.by_ref().take(MAX_ENTRY_BYTES + 1), &mut output)
                .map_err(|error| io_error("extract ugoira frame", error))?;
            if copied > MAX_ENTRY_BYTES {
                return Err(invalid("ugoira frame exceeds the size limit"));
            }
            if copied != expected_size {
                return Err(invalid("ugoira frame size does not match its ZIP metadata"));
            }
        }

        if required.iter().any(|name| !extracted.contains(*name)) {
            return Err(invalid("ugoira archive is missing required frames"));
        }
        Ok(staging)
    })
    .await
    .map_err(|_| invalid("ugoira extraction panicked"))?
}

fn validate_frame_names(frames: &[UgoiraFrame]) -> Result<(), ApiError> {
    if frames.is_empty() {
        return Err(invalid("ugoira metadata contains no frames"));
    }
    if frames.len() > MAX_ENTRIES {
        return Err(invalid("ugoira metadata contains too many frames"));
    }
    let mut names = HashSet::with_capacity(frames.len());
    for frame in frames {
        validate_relative_path(&frame.file)?;
        if !names.insert(frame.file.as_str()) {
            return Err(invalid("ugoira metadata contains duplicate frame names"));
        }
    }
    Ok(())
}

fn validate_relative_path(name: &str) -> Result<(), ApiError> {
    let path = Path::new(name);
    if name.is_empty()
        || name.contains('\\')
        || path.is_absolute()
        || path.components().any(|component| {
            matches!(
                component,
                Component::ParentDir | Component::RootDir | Component::Prefix(_)
            )
        })
    {
        return Err(invalid("ugoira archive contains an unsafe path"));
    }
    Ok(())
}

fn cache_is_complete(cache_dir: &Path, frames: &[UgoiraFrame]) -> bool {
    cache_dir.join(COMPLETE_MARKER).is_file()
        && frames
            .iter()
            .all(|frame| cache_dir.join(&frame.file).is_file())
}

fn playback(cache_dir: &Path, frames: Vec<UgoiraFrame>) -> UgoiraPlayback {
    UgoiraPlayback {
        frames: frames
            .into_iter()
            .map(|frame| UgoiraFrame {
                file: cache_dir.join(frame.file).to_string_lossy().into_owned(),
                delay_millis: normalized_frame_delay(frame.delay_millis),
            })
            .collect(),
    }
}

fn normalized_frame_delay(delay_millis: i32) -> i32 {
    if delay_millis >= MIN_VALID_FRAME_DELAY_MILLIS {
        delay_millis
    } else {
        DEFAULT_FRAME_DELAY_MILLIS
    }
}

fn remove_if_exists(path: &Path) -> Result<(), ApiError> {
    if path.is_dir() {
        fs::remove_dir_all(path).map_err(|error| io_error("remove ugoira directory", error))?;
    } else if path.exists() {
        fs::remove_file(path).map_err(|error| io_error("remove ugoira file", error))?;
    }
    Ok(())
}

fn invalid(detail: &str) -> ApiError {
    invalid_request(detail)
}

fn zip_error(error: zip::result::ZipError) -> ApiError {
    ApiError::InvalidRequest {
        detail: format!("invalid ugoira ZIP: {error}"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use std::path::PathBuf;
    use zip::write::SimpleFileOptions;

    fn temp_dir(name: &str) -> PathBuf {
        let nonce = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let dir =
            std::env::temp_dir().join(format!("palleria-{name}-{}-{nonce}", std::process::id(),));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    fn zip_with(path: &Path, entries: &[(&str, &[u8])]) {
        let file = File::create(path).unwrap();
        let mut writer = zip::ZipWriter::new(file);
        for (name, contents) in entries {
            writer
                .start_file(*name, SimpleFileOptions::default())
                .unwrap();
            writer.write_all(contents).unwrap();
        }
        writer.finish().unwrap();
    }

    #[test]
    fn extracts_only_required_frames_and_marks_cache_complete() {
        let root = temp_dir("extract");
        let zip_path = root.join("frames.zip");
        zip_with(&zip_path, &[("000.jpg", b"a"), ("unused.jpg", b"b")]);
        let cache = root.join("cache");
        let result = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(async {
                prepare(
                    &zip_path,
                    &cache,
                    vec![UgoiraFrame {
                        file: "000.jpg".into(),
                        delay_millis: 10,
                    }],
                )
                .await
            })
            .unwrap();
        assert_eq!(result.frames[0].delay_millis, DEFAULT_FRAME_DELAY_MILLIS);
        assert!(cache.join("000.jpg").is_file());
        assert!(!cache.join("unused.jpg").exists());
        assert!(cache.join(COMPLETE_MARKER).is_file());
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn rejects_unsafe_frame_paths() {
        let root = temp_dir("unsafe");
        let result = tokio::runtime::Runtime::new().unwrap().block_on(async {
            prepare(
                &root.join("missing.zip"),
                &root.join("cache"),
                vec![UgoiraFrame {
                    file: "../escape.jpg".into(),
                    delay_millis: 20,
                }],
            )
            .await
        });
        assert!(matches!(result, Err(ApiError::InvalidRequest { .. })));
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn preserves_valid_frame_delays_and_replaces_invalid_ones() {
        assert_eq!(normalized_frame_delay(80), 80);
        assert_eq!(normalized_frame_delay(20), 20);
        assert_eq!(normalized_frame_delay(19), DEFAULT_FRAME_DELAY_MILLIS);
        assert_eq!(normalized_frame_delay(0), DEFAULT_FRAME_DELAY_MILLIS);
    }

    #[test]
    fn rejects_archives_missing_required_frames() {
        let root = temp_dir("missing");
        let zip_path = root.join("frames.zip");
        zip_with(&zip_path, &[("other.jpg", b"x")]);
        let result = tokio::runtime::Runtime::new().unwrap().block_on(async {
            prepare(
                &zip_path,
                &root.join("cache"),
                vec![UgoiraFrame {
                    file: "000.jpg".into(),
                    delay_millis: 20,
                }],
            )
            .await
        });
        assert!(matches!(result, Err(ApiError::InvalidRequest { .. })));
        fs::remove_dir_all(root).unwrap();
    }

    #[tokio::test]
    async fn rejects_duplicate_normalized_archive_entries() {
        let root = temp_dir("duplicate-archive");
        let zip_path = root.join("frames.zip");
        zip_with(
            &zip_path,
            &[("nested/000.jpg", b"first"), ("nested\\000.jpg", b"second")],
        );
        let cache = root.join("cache");
        let error = prepare(
            &zip_path,
            &cache,
            vec![UgoiraFrame {
                file: "nested/000.jpg".into(),
                delay_millis: 20,
            }],
        )
        .await
        .unwrap_err();
        assert!(matches!(
            error,
            ApiError::InvalidRequest { detail }
                if detail == "ugoira archive contains duplicate frame entries"
        ));
        assert!(!cache.exists());
        assert_eq!(fs::read_dir(&root).unwrap().count(), 1);
        fs::remove_dir_all(root).unwrap();
    }

    #[tokio::test]
    async fn removes_partially_extracted_frames_and_preserves_existing_cache_on_failure() {
        let root = temp_dir("partial-extract");
        let zip_path = root.join("frames.zip");
        zip_with(&zip_path, &[("nested/000.jpg", b"first")]);
        let cache = root.join("cache");
        fs::create_dir_all(&cache).unwrap();
        fs::write(cache.join("old.jpg"), b"cached").unwrap();

        let error = prepare(
            &zip_path,
            &cache,
            vec![
                UgoiraFrame {
                    file: "nested/000.jpg".into(),
                    delay_millis: 20,
                },
                UgoiraFrame {
                    file: "nested/001.jpg".into(),
                    delay_millis: 20,
                },
            ],
        )
        .await
        .unwrap_err();
        assert!(matches!(
            error,
            ApiError::InvalidRequest { detail }
                if detail == "ugoira archive is missing required frames"
        ));
        assert_eq!(fs::read(cache.join("old.jpg")).unwrap(), b"cached");
        assert!(!cache.join(COMPLETE_MARKER).exists());
        assert_eq!(fs::read_dir(&root).unwrap().count(), 2);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn rejects_duplicate_frame_names() {
        let frames = vec![
            UgoiraFrame {
                file: "000.jpg".into(),
                delay_millis: 20,
            },
            UgoiraFrame {
                file: "000.jpg".into(),
                delay_millis: 30,
            },
        ];
        let error = validate_frame_names(&frames).unwrap_err();
        assert!(matches!(
            error,
            ApiError::InvalidRequest { detail }
                if detail == "ugoira metadata contains duplicate frame names"
        ));
    }

    #[test]
    fn removes_staging_data_after_a_corrupt_archive() {
        let root = temp_dir("corrupt");
        let zip_path = root.join("frames.zip");
        fs::write(&zip_path, b"not-a-zip").unwrap();
        let result = tokio::runtime::Runtime::new().unwrap().block_on(async {
            prepare(
                &zip_path,
                &root.join("cache"),
                vec![UgoiraFrame {
                    file: "000.jpg".into(),
                    delay_millis: 20,
                }],
            )
            .await
        });
        assert!(matches!(result, Err(ApiError::InvalidRequest { .. })));
        assert!(fs::read_dir(&root).unwrap().all(|entry| {
            !entry
                .unwrap()
                .file_name()
                .to_string_lossy()
                .contains(".staging-")
        }));
        fs::remove_dir_all(root).unwrap();
    }
}
