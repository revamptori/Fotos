use crate::database::Database;
use crate::error::Result;
use serde::Serialize;

#[derive(Debug, Serialize)]
pub struct StorageInfo {
    pub total_items: i64,
    pub total_bytes: i64,
}

pub fn toggle_favorite(db: &Database, id: i64) -> Result<bool> {
    db.toggle_favorite(id)
}

pub fn set_album_hidden(db: &Database, bucket_id: &str, bucket_name: &str, hidden: bool) -> Result<()> {
    db.set_album_hidden(bucket_id, bucket_name, hidden)
}

pub fn get_storage_info(db: &Database) -> Result<StorageInfo> {
    Ok(StorageInfo {
        total_items: db.get_total_count()?,
        total_bytes: db.get_total_storage_bytes()?,
    })
}
