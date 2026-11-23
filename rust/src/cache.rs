use crate::error::Result;
use lru::LruCache;
use serde::Serialize;
use std::fs;
use std::num::NonZeroUsize;
use std::path::{Path, PathBuf};

#[derive(Debug, Serialize)]
pub struct CacheStats {
    pub memory_entries: usize,
    pub memory_bytes: usize,
    pub disk_bytes: u64,
}

pub struct ThumbnailCache {
    memory: LruCache<i64, Vec<u8>>,
    memory_bytes: usize,
    max_memory_bytes: usize,
    disk_dir: PathBuf,
}

impl ThumbnailCache {
    pub fn new<P: AsRef<Path>>(disk_dir: P, max_entries: usize, max_memory_bytes: usize) -> Result<Self> {
        let dir = disk_dir.as_ref().to_path_buf();
        if !dir.exists() {
            fs::create_dir_all(&dir)?;
        }
        let cap = NonZeroUsize::new(max_entries.max(16)).unwrap();
        Ok(Self {
            memory: LruCache::new(cap),
            memory_bytes: 0,
            max_memory_bytes,
            disk_dir: dir,
        })
    }

    pub fn get(&mut self, id: i64) -> Option<Vec<u8>> {
        if let Some(bytes) = self.memory.get(&id) {
            return Some(bytes.clone());
        }
        let disk_path = self.disk_path(id);
        if disk_path.exists() {
            if let Ok(bytes) = fs::read(&disk_path) {
                self.put_memory(id, bytes.clone());
                return Some(bytes);
            }
        }
        None
    }

    pub fn put(&mut self, id: i64, bytes: Vec<u8>) {
        let disk_path = self.disk_path(id);
        let _ = fs::write(disk_path, &bytes);
        self.put_memory(id, bytes);
    }

    fn put_memory(&mut self, id: i64, bytes: Vec<u8>) {
        let len = bytes.len();
        if len > self.max_memory_bytes {
            return;
        }
        while self.memory_bytes + len > self.max_memory_bytes {
            if let Some((_, evicted)) = self.memory.pop_lru() {
                self.memory_bytes = self.memory_bytes.saturating_sub(evicted.len());
            } else {
                break;
            }
        }
        if let Some(prev) = self.memory.put(id, bytes) {
            self.memory_bytes = self.memory_bytes.saturating_sub(prev.len());
        }
        self.memory_bytes += len;
    }

    pub fn clear(&mut self) -> Result<()> {
        self.memory.clear();
        self.memory_bytes = 0;
        if self.disk_dir.exists() {
            for entry in fs::read_dir(&self.disk_dir)? {
                let entry = entry?;
                if entry.path().is_file() {
                    let _ = fs::remove_file(entry.path());
                }
            }
        }
        Ok(())
    }

    pub fn stats(&self) -> CacheStats {
        let mut disk_bytes = 0u64;
        if let Ok(entries) = fs::read_dir(&self.disk_dir) {
            for entry in entries.flatten() {
                if let Ok(meta) = entry.metadata() {
                    if meta.is_file() {
                        disk_bytes += meta.len();
                    }
                }
            }
        }
        CacheStats {
            memory_entries: self.memory.len(),
            memory_bytes: self.memory_bytes,
            disk_bytes,
        }
    }

    fn disk_path(&self, id: i64) -> PathBuf {
        self.disk_dir.join(format!("{}.thumb", id))
    }
}
