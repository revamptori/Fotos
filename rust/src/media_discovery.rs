use crate::database::Database;
use crate::error::Result;
use crate::metadata::MediaItem;
use serde::Serialize;
use std::collections::HashSet;

#[derive(Debug, Serialize)]
pub struct SyncReport {
    pub upserted: usize,
    pub removed: usize,
    pub total: i64,
}

pub fn sync_from_mediastore(
    db: &mut Database,
    items: &[MediaItem],
    prune_missing: bool,
) -> Result<SyncReport> {
    let upserted = db.upsert_media_batch(items)?;
    let removed = if prune_missing {
        let active_ids: HashSet<i64> = items.iter().map(|i| i.id).collect();
        db.prune_missing_ids(&active_ids)?
    } else {
        0
    };
    let total = db.get_total_count()?;
    Ok(SyncReport {
        upserted,
        removed,
        total,
    })
}
