use bytes::Bytes;
use futures_util::StreamExt;
use std::collections::HashMap;
use std::fs;
use std::io::{BufWriter, Write};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, OnceLock, Weak};
use tokio::sync::{Mutex as AsyncMutex, mpsc};

use super::{PixivHttpClient, transport};
use crate::error::{ApiError, invalid_request, io_error, network_error};
use crate::models::{UgoiraFrame, UgoiraPlayback};
use crate::temp_path::TempPath;
use crate::ugoira;

const MAX_ARCHIVE_BYTES: u64 = 512 * 1024 * 1024;
const DOWNLOAD_CHANNEL_DEPTH: usize = 4;
static CACHE_LOCKS: OnceLock<Mutex<HashMap<PathBuf, Weak<AsyncMutex<()>>>>> = OnceLock::new();

pub(super) async fn prepare(
    client: &PixivHttpClient,
    url: String,
    headers: HashMap<String, String>,
    cache_dir: String,
    frames: Vec<UgoiraFrame>,
) -> Result<UgoiraPlayback, ApiError> {
    let cache_dir = PathBuf::from(cache_dir);
    let cache_lock = cache_lock(&cache_dir)?;
    let _guard = cache_lock.lock().await;

    if let Some(playback) = ugoira::cached(&cache_dir, &frames) {
        return Ok(playback);
    }

    let headers = transport::request_headers(client, headers, None)?;
    let response = client
        .client
        .get(&url)
        .headers(headers)
        .send()
        .await
        .map_err(network_error)?;
    let response = transport::ensure_success(response).await?;
    if response
        .content_length()
        .is_some_and(|length| length > MAX_ARCHIVE_BYTES)
    {
        return Err(archive_too_large());
    }

    let parent = cache_dir
        .parent()
        .ok_or_else(|| invalid_request("ugoira cache directory has no parent"))?;
    fs::create_dir_all(parent).map_err(|error| io_error("create ugoira cache parent", error))?;
    let zip_path = TempPath::sibling(&cache_dir, "zip.download");
    download_to_file(response, zip_path.path(), MAX_ARCHIVE_BYTES).await?;
    ugoira::prepare(zip_path.path(), &cache_dir, frames).await
}

/// Streams the archive to disk through the blocking pool. Buffering the whole download (up to
/// `MAX_ARCHIVE_BYTES`) on the heap doubles peak memory on low-RAM devices, and writing every
/// network chunk synchronously would block the async worker.
async fn download_to_file(
    response: reqwest::Response,
    path: &Path,
    limit: u64,
) -> Result<(), ApiError> {
    let (sender, receiver) = mpsc::channel::<Bytes>(DOWNLOAD_CHANNEL_DEPTH);
    let writer_path = path.to_owned();
    let writer = tokio::task::spawn_blocking(move || write_chunks(&writer_path, receiver));

    let mut stream = response.bytes_stream();
    let mut total = 0_u64;
    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(network_error)?;
        total = total.saturating_add(chunk.len() as u64);
        if total > limit {
            // Drop the sender so the writer drains what it already received and closes the
            // partial file; the caller's TempPath removes it.
            drop(sender);
            let _ = writer.await;
            return Err(archive_too_large());
        }
        if sender.send(chunk).await.is_err() {
            break;
        }
    }
    drop(sender);
    writer.await.map_err(|_| {
        io_error(
            "write ugoira download",
            std::io::Error::other("worker panicked"),
        )
    })??;
    Ok(())
}

fn write_chunks(path: &Path, mut receiver: mpsc::Receiver<Bytes>) -> Result<(), ApiError> {
    let output = fs::OpenOptions::new()
        .create_new(true)
        .write(true)
        .open(path)
        .map_err(|error| io_error("create ugoira download", error))?;
    let mut output = BufWriter::new(output);
    while let Some(chunk) = receiver.blocking_recv() {
        output
            .write_all(&chunk)
            .map_err(|error| io_error("write ugoira download", error))?;
    }
    output
        .flush()
        .map_err(|error| io_error("write ugoira download", error))
}

fn cache_lock(cache_dir: &Path) -> Result<Arc<AsyncMutex<()>>, ApiError> {
    let locks = CACHE_LOCKS.get_or_init(|| Mutex::new(HashMap::new()));
    let mut locks = locks
        .lock()
        .map_err(|_| invalid_request("ugoira lock registry is poisoned"))?;
    if let Some(lock) = locks.get(cache_dir).and_then(Weak::upgrade) {
        return Ok(lock);
    }
    locks.retain(|_, lock| lock.strong_count() > 0);
    let lock = Arc::new(AsyncMutex::new(()));
    locks.insert(cache_dir.to_path_buf(), Arc::downgrade(&lock));
    Ok(lock)
}

fn archive_too_large() -> ApiError {
    invalid_request("ugoira archive exceeds the download limit")
}
