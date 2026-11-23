use crate::albums::{get_albums, AlbumSort};
use crate::cache::ThumbnailCache;
use crate::database::Database;
use crate::file_ops::{get_storage_info, set_album_hidden, toggle_favorite};
use crate::media_discovery::sync_from_mediastore;
use crate::metadata::MediaItem;
use crate::search::search_media;
use crate::thumbnails::{get_cached_thumbnail, store_cached_thumbnail};
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};

struct EngineState {
    db: Database,
    thumb_cache: ThumbnailCache,
}

static ENGINE: OnceLock<Mutex<EngineState>> = OnceLock::new();

fn to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map(|js| js.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn with_engine<F, R>(f: F) -> Option<R>
where
    F: FnOnce(&mut EngineState) -> R,
{
    let lock = ENGINE.get()?;
    let mut guard = lock.lock().ok()?;
    Some(f(&mut guard))
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeInit(
    mut env: JNIEnv,
    _class: JClass,
    db_dir: JString,
    cache_dir: JString,
) -> jboolean {
    #[cfg(target_os = "android")]
    {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Info)
                .with_tag("FotosRust"),
        );
    }

    let db_dir_str: String = match env.get_string(&db_dir) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };
    let cache_dir_str: String = match env.get_string(&cache_dir) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };

    if ENGINE.get().is_some() {
        return JNI_TRUE;
    }

    let db_path = PathBuf::from(db_dir_str).join("fotos.db");
    let db = match Database::open(db_path) {
        Ok(d) => d,
        Err(e) => {
            log::error!("Failed to open database: {}", e);
            return JNI_FALSE;
        }
    };

    // 16 MB bounded in-memory LRU cache + disk cache
    let thumb_cache = match ThumbnailCache::new(
        PathBuf::from(cache_dir_str).join("thumbs"),
        256,
        16 * 1024 * 1024,
    ) {
        Ok(c) => c,
        Err(e) => {
            log::error!("Failed to init thumbnail cache: {}", e);
            return JNI_FALSE;
        }
    };

    let _ = ENGINE.set(Mutex::new(EngineState { db, thumb_cache }));
    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeSyncMedia(
    mut env: JNIEnv,
    _class: JClass,
    items_json: JString,
    prune_missing: jboolean,
) -> jstring {
    let json_str: String = match env.get_string(&items_json) {
        Ok(s) => s.into(),
        Err(_) => return to_jstring(&mut env, "{\"error\":\"invalid_string\"}"),
    };
    let items: Vec<MediaItem> = match serde_json::from_str(&json_str) {
        Ok(v) => v,
        Err(e) => {
            log::error!("JSON parse error: {}", e);
            return to_jstring(&mut env, "{\"error\":\"json_parse\"}");
        }
    };
    let prune = prune_missing != JNI_FALSE;
    let res = with_engine(|state| {
        sync_from_mediastore(&mut state.db, &items, prune)
            .and_then(|r| Ok(serde_json::to_string(&r)?))
    });
    match res {
        Some(Ok(s)) => to_jstring(&mut env, &s),
        _ => to_jstring(&mut env, "{\"error\":\"sync_failed\"}"),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeGetMediaPaginated(
    mut env: JNIEnv,
    _class: JClass,
    offset: jlong,
    limit: jlong,
    include_hidden: jboolean,
    sort_asc: jboolean,
) -> jstring {
    let res = with_engine(|state| {
        state
            .db
            .get_media_paginated(offset, limit, include_hidden != JNI_FALSE, sort_asc != JNI_FALSE)
            .and_then(|items| Ok(serde_json::to_string(&items)?))
    });
    match res {
        Some(Ok(s)) => to_jstring(&mut env, &s),
        _ => to_jstring(&mut env, "[]"),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeGetAlbumMediaPaginated(
    mut env: JNIEnv,
    _class: JClass,
    bucket_id: JString,
    offset: jlong,
    limit: jlong,
    sort_asc: jboolean,
) -> jstring {
    let bucket: String = match env.get_string(&bucket_id) {
        Ok(s) => s.into(),
        Err(_) => return to_jstring(&mut env, "[]"),
    };
    let res = with_engine(|state| {
        state
            .db
            .get_album_media_paginated(&bucket, offset, limit, sort_asc != JNI_FALSE)
            .and_then(|items| Ok(serde_json::to_string(&items)?))
    });
    match res {
        Some(Ok(s)) => to_jstring(&mut env, &s),
        _ => to_jstring(&mut env, "[]"),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeGetAlbums(
    mut env: JNIEnv,
    _class: JClass,
    include_hidden: jboolean,
    sort_mode: jint,
) -> jstring {
    let sort = AlbumSort::from_i32(sort_mode);
    let res = with_engine(|state| {
        get_albums(&state.db, include_hidden != JNI_FALSE, sort)
            .and_then(|albums| Ok(serde_json::to_string(&albums)?))
    });
    match res {
        Some(Ok(s)) => to_jstring(&mut env, &s),
        _ => to_jstring(&mut env, "[]"),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeSearchMedia(
    mut env: JNIEnv,
    _class: JClass,
    query: JString,
    limit: jlong,
) -> jstring {
    let q: String = match env.get_string(&query) {
        Ok(s) => s.into(),
        Err(_) => return to_jstring(&mut env, "[]"),
    };
    let res = with_engine(|state| {
        search_media(&state.db, &q, limit).and_then(|items| Ok(serde_json::to_string(&items)?))
    });
    match res {
        Some(Ok(s)) => to_jstring(&mut env, &s),
        _ => to_jstring(&mut env, "[]"),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeDeleteMedia(
    _env: JNIEnv,
    _class: JClass,
    id: jlong,
) -> jboolean {
    let res = with_engine(|state| state.db.delete_media_by_id(id).unwrap_or(false));
    if res.unwrap_or(false) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeToggleFavorite(
    _env: JNIEnv,
    _class: JClass,
    id: jlong,
) -> jboolean {
    let res = with_engine(|state| toggle_favorite(&state.db, id).unwrap_or(false));
    if res.unwrap_or(false) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeSetAlbumHidden(
    mut env: JNIEnv,
    _class: JClass,
    bucket_id: JString,
    bucket_name: JString,
    hidden: jboolean,
) -> jboolean {
    let bid: String = match env.get_string(&bucket_id) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };
    let bname: String = match env.get_string(&bucket_name) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };
    let res = with_engine(|state| set_album_hidden(&state.db, &bid, &bname, hidden != JNI_FALSE).is_ok());
    if res.unwrap_or(false) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeGetHiddenAlbums(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let res = with_engine(|state| {
        state
            .db
            .get_hidden_albums()
            .and_then(|list| Ok(serde_json::to_string(&list)?))
    });
    match res {
        Some(Ok(s)) => to_jstring(&mut env, &s),
        _ => to_jstring(&mut env, "[]"),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeGetCachedThumbnail<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    id: jlong,
) -> JByteArray<'local> {
    let bytes = with_engine(|state| get_cached_thumbnail(&mut state.thumb_cache, id)).flatten();
    match bytes {
        Some(data) => env
            .byte_array_from_slice(&data)
            .unwrap_or_else(|_| JByteArray::default()),
        None => JByteArray::default(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativePutCachedThumbnail(
    env: JNIEnv,
    _class: JClass,
    id: jlong,
    data: JByteArray,
) {
    if let Ok(bytes) = env.convert_byte_array(&data) {
        with_engine(|state| store_cached_thumbnail(&mut state.thumb_cache, id, bytes));
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeClearCache(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let res = with_engine(|state| state.thumb_cache.clear().is_ok());
    if res.unwrap_or(false) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeGetCacheStats(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let res = with_engine(|state| serde_json::to_string(&state.thumb_cache.stats()));
    match res {
        Some(Ok(s)) => to_jstring(&mut env, &s),
        _ => to_jstring(&mut env, "{}"),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fotos_app_engine_RustEngine_nativeGetStorageInfo(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let res = with_engine(|state| {
        get_storage_info(&state.db).and_then(|info| Ok(serde_json::to_string(&info)?))
    });
    match res {
        Some(Ok(s)) => to_jstring(&mut env, &s),
        _ => to_jstring(&mut env, "{}"),
    }
}
