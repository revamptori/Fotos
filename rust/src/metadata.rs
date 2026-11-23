use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum MediaType {
    Image,
    Video,
}

impl MediaType {
    pub fn as_str(&self) -> &'static str {
        match self {
            MediaType::Image => "image",
            MediaType::Video => "video",
        }
    }

    pub fn from_str(s: &str) -> Self {
        if s.eq_ignore_ascii_case("video") {
            MediaType::Video
        } else {
            MediaType::Image
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MediaItem {
    pub id: i64,
    pub uri: String,
    pub display_name: String,
    pub bucket_id: String,
    pub bucket_name: String,
    pub media_type: MediaType,
    pub mime_type: String,
    pub size_bytes: i64,
    pub width: i32,
    pub height: i32,
    pub duration_ms: i64,
    pub date_added_sec: i64,
    pub date_modified_sec: i64,
    pub is_favorite: bool,
}

/// Convert a Unix timestamp (seconds) into a YYYY-MM-DD string (UTC-based fast civil calculation).
/// Avoids pulling in heavy chrono/time crates to keep binary size minimal.
pub fn timestamp_to_date_str(ts_sec: i64) -> String {
    let days = ts_sec.div_euclid(86400) + 719468;
    let era = if days >= 0 { days } else { days - 146096 } / 146097;
    let doe = days - era * 146097;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let year = if m <= 2 { y + 1 } else { y };
    format!("{:04}-{:02}-{:02}", year, m, d)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_timestamp_to_date_str() {
        assert_eq!(timestamp_to_date_str(0), "1970-01-01");
        assert_eq!(timestamp_to_date_str(1700000000), "2023-11-14");
    }
}
