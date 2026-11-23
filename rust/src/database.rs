use crate::error::Result;
use crate::metadata::{timestamp_to_date_str, MediaItem, MediaType};
use rusqlite::{params, Connection, OptionalExtension};
use std::collections::HashSet;
use std::path::Path;

pub struct Database {
    conn: Connection,
}

impl Database {
    pub fn open<P: AsRef<Path>>(path: P) -> Result<Self> {
        let conn = Connection::open(path)?;
        conn.execute_batch(
            "PRAGMA journal_mode = WAL;
             PRAGMA synchronous = NORMAL;
             PRAGMA temp_store = MEMORY;
             PRAGMA cache_size = -4000;",
        )?;
        let db = Self { conn };
        db.init_schema()?;
        Ok(db)
    }

    pub fn open_in_memory() -> Result<Self> {
        let conn = Connection::open_in_memory()?;
        let db = Self { conn };
        db.init_schema()?;
        Ok(db)
    }

    fn init_schema(&self) -> Result<()> {
        self.conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS media (
                id INTEGER PRIMARY KEY,
                uri TEXT NOT NULL,
                display_name TEXT NOT NULL,
                bucket_id TEXT NOT NULL,
                bucket_name TEXT NOT NULL,
                media_type TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                date_added_sec INTEGER NOT NULL,
                date_modified_sec INTEGER NOT NULL,
                date_key TEXT NOT NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0
            );

            CREATE INDEX IF NOT EXISTS idx_media_date ON media(date_modified_sec DESC, id DESC);
            CREATE INDEX IF NOT EXISTS idx_media_bucket ON media(bucket_id, date_modified_sec DESC);
            CREATE INDEX IF NOT EXISTS idx_media_name ON media(display_name COLLATE NOCASE);
            CREATE INDEX IF NOT EXISTS idx_media_date_key ON media(date_key);

            CREATE TABLE IF NOT EXISTS hidden_albums (
                bucket_id TEXT PRIMARY KEY,
                bucket_name TEXT NOT NULL
            );",
        )?;
        Ok(())
    }

    /// Incrementally sync a batch of MediaStore items into SQLite.
    /// Preserves `is_favorite` flag on existing items.
    pub fn upsert_media_batch(&mut self, items: &[MediaItem]) -> Result<usize> {
        let tx = self.conn.transaction()?;
        let mut count = 0usize;
        {
            let mut stmt = tx.prepare_cached(
                "INSERT INTO media (
                    id, uri, display_name, bucket_id, bucket_name, media_type,
                    mime_type, size_bytes, width, height, duration_ms,
                    date_added_sec, date_modified_sec, date_key, is_favorite
                ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)
                ON CONFLICT(id) DO UPDATE SET
                    uri = excluded.uri,
                    display_name = excluded.display_name,
                    bucket_id = excluded.bucket_id,
                    bucket_name = excluded.bucket_name,
                    media_type = excluded.media_type,
                    mime_type = excluded.mime_type,
                    size_bytes = excluded.size_bytes,
                    width = excluded.width,
                    height = excluded.height,
                    duration_ms = excluded.duration_ms,
                    date_added_sec = excluded.date_added_sec,
                    date_modified_sec = excluded.date_modified_sec,
                    date_key = excluded.date_key",
            )?;

            for item in items {
                let date_key = timestamp_to_date_str(item.date_modified_sec);
                stmt.execute(params![
                    item.id,
                    item.uri,
                    item.display_name,
                    item.bucket_id,
                    item.bucket_name,
                    item.media_type.as_str(),
                    item.mime_type,
                    item.size_bytes,
                    item.width,
                    item.height,
                    item.duration_ms,
                    item.date_added_sec,
                    item.date_modified_sec,
                    date_key,
                    if item.is_favorite { 1 } else { 0 },
                ])?;
                count += 1;
            }
        }
        tx.commit()?;
        Ok(count)
    }

    /// Remove media items that no longer exist in MediaStore.
    pub fn prune_missing_ids(&mut self, active_ids: &HashSet<i64>) -> Result<usize> {
        let mut existing_ids = Vec::new();
        {
            let mut stmt = self.conn.prepare("SELECT id FROM media")?;
            let rows = stmt.query_map([], |row| row.get::<_, i64>(0))?;
            for r in rows {
                let id = r?;
                if !active_ids.contains(&id) {
                    existing_ids.push(id);
                }
            }
        }
        if existing_ids.is_empty() {
            return Ok(0);
        }
        let tx = self.conn.transaction()?;
        let mut removed = 0usize;
        {
            let mut del = tx.prepare_cached("DELETE FROM media WHERE id = ?1")?;
            for id in existing_ids {
                removed += del.execute(params![id])?;
            }
        }
        tx.commit()?;
        Ok(removed)
    }

    pub fn delete_media_by_id(&self, id: i64) -> Result<bool> {
        let rows = self.conn.execute("DELETE FROM media WHERE id = ?1", params![id])?;
        Ok(rows > 0)
    }

    pub fn get_media_paginated(
        &self,
        offset: i64,
        limit: i64,
        include_hidden: bool,
        sort_asc: bool,
    ) -> Result<Vec<MediaItem>> {
        let order = if sort_asc { "ASC" } else { "DESC" };
        let hidden_clause = if include_hidden {
            ""
        } else {
            "WHERE bucket_id NOT IN (SELECT bucket_id FROM hidden_albums)"
        };
        let sql = format!(
            "SELECT id, uri, display_name, bucket_id, bucket_name, media_type,
                    mime_type, size_bytes, width, height, duration_ms,
                    date_added_sec, date_modified_sec, is_favorite
             FROM media
             {}
             ORDER BY date_modified_sec {}, id {}
             LIMIT ?1 OFFSET ?2",
            hidden_clause, order, order
        );
        let mut stmt = self.conn.prepare(&sql)?;
        let rows = stmt.query_map(params![limit, offset], Self::row_to_media_item)?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r?);
        }
        Ok(out)
    }

    pub fn get_album_media_paginated(
        &self,
        bucket_id: &str,
        offset: i64,
        limit: i64,
        sort_asc: bool,
    ) -> Result<Vec<MediaItem>> {
        let order = if sort_asc { "ASC" } else { "DESC" };
        let sql = format!(
            "SELECT id, uri, display_name, bucket_id, bucket_name, media_type,
                    mime_type, size_bytes, width, height, duration_ms,
                    date_added_sec, date_modified_sec, is_favorite
             FROM media
             WHERE bucket_id = ?1
             ORDER BY date_modified_sec {}, id {}
             LIMIT ?2 OFFSET ?3",
            order, order
        );
        let mut stmt = self.conn.prepare(&sql)?;
        let rows = stmt.query_map(params![bucket_id, limit, offset], Self::row_to_media_item)?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r?);
        }
        Ok(out)
    }

    pub fn toggle_favorite(&self, id: i64) -> Result<bool> {
        let current: Option<i64> = self
            .conn
            .query_row(
                "SELECT is_favorite FROM media WHERE id = ?1",
                params![id],
                |row| row.get(0),
            )
            .optional()?;
        let next = match current {
            Some(0) => 1,
            Some(_) => 0,
            None => return Ok(false),
        };
        self.conn.execute(
            "UPDATE media SET is_favorite = ?1 WHERE id = ?2",
            params![next, id],
        )?;
        Ok(next == 1)
    }

    pub fn set_album_hidden(&self, bucket_id: &str, bucket_name: &str, hidden: bool) -> Result<()> {
        if hidden {
            self.conn.execute(
                "INSERT OR REPLACE INTO hidden_albums (bucket_id, bucket_name) VALUES (?1, ?2)",
                params![bucket_id, bucket_name],
            )?;
        } else {
            self.conn.execute(
                "DELETE FROM hidden_albums WHERE bucket_id = ?1",
                params![bucket_id],
            )?;
        }
        Ok(())
    }

    pub fn get_hidden_albums(&self) -> Result<Vec<(String, String)>> {
        let mut stmt = self
            .conn
            .prepare("SELECT bucket_id, bucket_name FROM hidden_albums ORDER BY bucket_name COLLATE NOCASE")?;
        let rows = stmt.query_map([], |row| Ok((row.get(0)?, row.get(1)?)))?;
        let mut out = Vec::new();
        for r in rows {
            out.push(r?);
        }
        Ok(out)
    }

    pub fn get_total_count(&self) -> Result<i64> {
        let count: i64 = self
            .conn
            .query_row("SELECT COUNT(*) FROM media", [], |row| row.get(0))?;
        Ok(count)
    }

    pub fn get_total_storage_bytes(&self) -> Result<i64> {
        let total: i64 = self
            .conn
            .query_row("SELECT COALESCE(SUM(size_bytes), 0) FROM media", [], |row| {
                row.get(0)
            })?;
        Ok(total)
    }

    pub fn conn(&self) -> &Connection {
        &self.conn
    }

    pub(crate) fn row_to_media_item(row: &rusqlite::Row<'_>) -> rusqlite::Result<MediaItem> {
        let media_type_str: String = row.get(5)?;
        let fav: i64 = row.get(13)?;
        Ok(MediaItem {
            id: row.get(0)?,
            uri: row.get(1)?,
            display_name: row.get(2)?,
            bucket_id: row.get(3)?,
            bucket_name: row.get(4)?,
            media_type: MediaType::from_str(&media_type_str),
            mime_type: row.get(6)?,
            size_bytes: row.get(7)?,
            width: row.get(8)?,
            height: row.get(9)?,
            duration_ms: row.get(10)?,
            date_added_sec: row.get(11)?,
            date_modified_sec: row.get(12)?,
            is_favorite: fav != 0,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_item(id: i64, name: &str, bucket: &str, ts: i64) -> MediaItem {
        MediaItem {
            id,
            uri: format!("content://media/external/images/media/{}", id),
            display_name: name.to_string(),
            bucket_id: bucket.to_string(),
            bucket_name: format!("Album {}", bucket),
            media_type: MediaType::Image,
            mime_type: "image/jpeg".to_string(),
            size_bytes: 1024,
            width: 1920,
            height: 1080,
            duration_ms: 0,
            date_added_sec: ts,
            date_modified_sec: ts,
            is_favorite: false,
        }
    }

    #[test]
    fn test_upsert_and_pagination() {
        let mut db = Database::open_in_memory().unwrap();
        let items = vec![
            sample_item(1, "a.jpg", "b1", 100),
            sample_item(2, "b.jpg", "b1", 200),
            sample_item(3, "c.jpg", "b2", 300),
        ];
        assert_eq!(db.upsert_media_batch(&items).unwrap(), 3);
        assert_eq!(db.get_total_count().unwrap(), 3);

        let page = db.get_media_paginated(0, 2, false, false).unwrap();
        assert_eq!(page.len(), 2);
        assert_eq!(page[0].id, 3);
        assert_eq!(page[1].id, 2);
    }

    #[test]
    fn test_prune_missing() {
        let mut db = Database::open_in_memory().unwrap();
        let items = vec![
            sample_item(1, "a.jpg", "b1", 100),
            sample_item(2, "b.jpg", "b1", 200),
        ];
        db.upsert_media_batch(&items).unwrap();
        let mut active = HashSet::new();
        active.insert(2);
        let removed = db.prune_missing_ids(&active).unwrap();
        assert_eq!(removed, 1);
        assert_eq!(db.get_total_count().unwrap(), 1);
    }
}
