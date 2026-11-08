use std::fs::{self, File, OpenOptions};
use std::io;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

static TEMP_SEQUENCE: AtomicU64 = AtomicU64::new(0);

pub(crate) struct TempPath {
    path: PathBuf,
}

impl TempPath {
    pub(crate) fn sibling(target: &Path, label: &str) -> Self {
        let file_name = target
            .file_name()
            .and_then(|name| name.to_str())
            .unwrap_or("palleria");
        let sequence = TEMP_SEQUENCE.fetch_add(1, Ordering::Relaxed);
        let path = target.with_file_name(format!(
            "{file_name}.{label}-{}-{sequence}",
            std::process::id()
        ));
        Self { path }
    }

    pub(crate) fn path(&self) -> &Path {
        &self.path
    }

    #[cfg_attr(not(test), allow(dead_code))]
    pub(crate) fn create_file(&self) -> io::Result<File> {
        OpenOptions::new()
            .create_new(true)
            .write(true)
            .open(&self.path)
    }
}

impl Drop for TempPath {
    fn drop(&mut self) {
        if self.path.is_dir() {
            let _ = fs::remove_dir_all(&self.path);
        } else {
            let _ = fs::remove_file(&self.path);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sibling_paths_are_unique() {
        let target = std::env::temp_dir().join("palleria-cache");
        let first = TempPath::sibling(&target, "download");
        let second = TempPath::sibling(&target, "download");
        assert_ne!(first.path(), second.path());
        assert_eq!(first.path().parent(), target.parent());
    }

    #[test]
    fn drop_removes_a_created_file() {
        let target = std::env::temp_dir().join(format!(
            "palleria-temp-path-test-{}",
            TEMP_SEQUENCE.fetch_add(1, Ordering::Relaxed)
        ));
        let path = {
            let temporary = TempPath::sibling(&target, "download");
            let path = temporary.path().to_path_buf();
            temporary.create_file().unwrap();
            assert!(path.is_file());
            path
        };
        assert!(!path.exists());
    }
}
