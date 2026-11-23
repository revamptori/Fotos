use crate::cache::ThumbnailCache;

/// Retrieve cached thumbnail bytes if available.
pub fn get_cached_thumbnail(cache: &mut ThumbnailCache, id: i64) -> Option<Vec<u8>> {
    cache.get(id)
}

/// Store compressed thumbnail bytes (e.g. JPEG/WebP) into bounded LRU + disk cache.
pub fn store_cached_thumbnail(cache: &mut ThumbnailCache, id: i64, bytes: Vec<u8>) {
    if !bytes.is_empty() {
        cache.put(id, bytes);
    }
}
