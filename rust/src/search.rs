use crate::database::Database;
use crate::error::Result;
use crate::metadata::MediaItem;
use rusqlite::params;

pub fn search_media(db: &Database, query: &str, limit: i64) -> Result<Vec<MediaItem>> {
    let trimmed = query.trim();
    if trimmed.is_empty() {
        return Ok(Vec::new());
    }
    let pattern = format!("%{}%", trimmed);
    let mut stmt = db.conn().prepare(
        "SELECT id, uri, display_name, bucket_id, bucket_name, media_type,
                mime_type, size_bytes, width, height, duration_ms,
                date_added_sec, date_modified_sec, is_favorite
         FROM media
         WHERE bucket_id NOT IN (SELECT bucket_id FROM hidden_albums)
           AND (display_name LIKE ?1 OR bucket_name LIKE ?1 OR date_key LIKE ?1)
         ORDER BY date_modified_sec DESC, id DESC
         LIMIT ?2",
    )?;
    let rows = stmt.query_map(params![pattern, limit], Database::row_to_media_item)?;
    let mut out = Vec::new();
    for r in rows {
        out.push(r?);
    }
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::metadata::MediaType;

    #[test]
    fn test_search_media() {
        let mut db = Database::open_in_memory().unwrap();
        let items = vec![MediaItem {
            id: 1,
            uri: "u1".into(),
            display_name: "vacation_beach.jpg".into(),
            bucket_id: "b1".into(),
            bucket_name: "Camera".into(),
            media_type: MediaType::Image,
            mime_type: "image/jpeg".into(),
            size_bytes: 100,
            width: 100,
            height: 100,
            duration_ms: 0,
            date_added_sec: 1700000000,
            date_modified_sec: 1700000000,
            is_favorite: false,
        }];
        db.upsert_media_batch(&items).unwrap();

        assert_eq!(search_media(&db, "beach", 50).unwrap().len(), 1);
        assert_eq!(search_media(&db, "Camera", 50).unwrap().len(), 1);
        assert_eq!(search_media(&db, "2023-11", 50).unwrap().len(), 1);
        assert_eq!(search_media(&db, "nonexistent", 50).unwrap().len(), 0);
    }
}
