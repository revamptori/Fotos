use crate::database::Database;
use crate::error::Result;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Album {
    pub bucket_id: String,
    pub bucket_name: String,
    pub cover_uri: String,
    pub cover_media_type: String,
    pub media_count: i64,
    pub latest_modified_sec: i64,
    pub is_hidden: bool,
}

#[derive(Debug, Clone, Copy)]
pub enum AlbumSort {
    LatestDesc,
    NameAsc,
    CountDesc,
}

impl AlbumSort {
    pub fn from_i32(v: i32) -> Self {
        match v {
            1 => AlbumSort::NameAsc,
            2 => AlbumSort::CountDesc,
            _ => AlbumSort::LatestDesc,
        }
    }
}

pub fn get_albums(db: &Database, include_hidden: bool, sort: AlbumSort) -> Result<Vec<Album>> {
    let order_clause = match sort {
        AlbumSort::LatestDesc => "latest_modified_sec DESC, bucket_name COLLATE NOCASE ASC",
        AlbumSort::NameAsc => "bucket_name COLLATE NOCASE ASC",
        AlbumSort::CountDesc => "media_count DESC, bucket_name COLLATE NOCASE ASC",
    };
    let hidden_filter = if include_hidden {
        ""
    } else {
        "WHERE m.bucket_id NOT IN (SELECT bucket_id FROM hidden_albums)"
    };

    let sql = format!(
        "SELECT
            m.bucket_id,
            m.bucket_name,
            (SELECT uri FROM media m2 WHERE m2.bucket_id = m.bucket_id ORDER BY m2.date_modified_sec DESC, m2.id DESC LIMIT 1) AS cover_uri,
            (SELECT media_type FROM media m2 WHERE m2.bucket_id = m.bucket_id ORDER BY m2.date_modified_sec DESC, m2.id DESC LIMIT 1) AS cover_media_type,
            COUNT(*) AS media_count,
            MAX(m.date_modified_sec) AS latest_modified_sec,
            EXISTS(SELECT 1 FROM hidden_albums h WHERE h.bucket_id = m.bucket_id) AS is_hidden
         FROM media m
         {}
         GROUP BY m.bucket_id
         ORDER BY {}",
        hidden_filter, order_clause
    );

    let mut stmt = db.conn().prepare(&sql)?;
    let rows = stmt.query_map([], |row| {
        let is_hidden_int: i64 = row.get(6)?;
        Ok(Album {
            bucket_id: row.get(0)?,
            bucket_name: row.get(1)?,
            cover_uri: row.get::<_, Option<String>>(2)?.unwrap_or_default(),
            cover_media_type: row
                .get::<_, Option<String>>(3)?
                .unwrap_or_else(|| "image".to_string()),
            media_count: row.get(4)?,
            latest_modified_sec: row.get(5)?,
            is_hidden: is_hidden_int != 0,
        })
    })?;

    let mut out = Vec::new();
    for r in rows {
        out.push(r?);
    }
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::metadata::{MediaItem, MediaType};

    #[test]
    fn test_get_albums() {
        let mut db = Database::open_in_memory().unwrap();
        let items = vec![
            MediaItem {
                id: 1,
                uri: "u1".into(),
                display_name: "a.jpg".into(),
                bucket_id: "b1".into(),
                bucket_name: "Camera".into(),
                media_type: MediaType::Image,
                mime_type: "image/jpeg".into(),
                size_bytes: 100,
                width: 100,
                height: 100,
                duration_ms: 0,
                date_added_sec: 10,
                date_modified_sec: 10,
                is_favorite: false,
            },
            MediaItem {
                id: 2,
                uri: "u2".into(),
                display_name: "b.jpg".into(),
                bucket_id: "b2".into(),
                bucket_name: "Screenshots".into(),
                media_type: MediaType::Image,
                mime_type: "image/png".into(),
                size_bytes: 200,
                width: 100,
                height: 100,
                duration_ms: 0,
                date_added_sec: 20,
                date_modified_sec: 20,
                is_favorite: false,
            },
        ];
        db.upsert_media_batch(&items).unwrap();
        let albums = get_albums(&db, false, AlbumSort::LatestDesc).unwrap();
        assert_eq!(albums.len(), 2);
        assert_eq!(albums[0].bucket_name, "Screenshots");
    }
}
