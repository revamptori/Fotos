package com.fotos.app.engine

import org.json.JSONArray
import org.json.JSONObject

data class MediaItem(
    val id: Long,
    val uri: String,
    val displayName: String,
    val bucketId: String,
    val bucketName: String,
    val mediaType: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val isFavorite: Boolean
) {
    val isVideo: Boolean get() = mediaType == "video"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("uri", uri)
        put("display_name", displayName)
        put("bucket_id", bucketId)
        put("bucket_name", bucketName)
        put("media_type", mediaType)
        put("mime_type", mimeType)
        put("size_bytes", sizeBytes)
        put("width", width)
        put("height", height)
        put("duration_ms", durationMs)
        put("date_added_sec", dateAddedSec)
        put("date_modified_sec", dateModifiedSec)
        put("is_favorite", isFavorite)
    }

    companion object {
        fun fromJson(obj: JSONObject): MediaItem = MediaItem(
            id = obj.getLong("id"),
            uri = obj.getString("uri"),
            displayName = obj.getString("display_name"),
            bucketId = obj.getString("bucket_id"),
            bucketName = obj.getString("bucket_name"),
            mediaType = obj.getString("media_type"),
            mimeType = obj.getString("mime_type"),
            sizeBytes = obj.getLong("size_bytes"),
            width = obj.getInt("width"),
            height = obj.getInt("height"),
            durationMs = obj.getLong("duration_ms"),
            dateAddedSec = obj.getLong("date_added_sec"),
            dateModifiedSec = obj.getLong("date_modified_sec"),
            isFavorite = obj.optBoolean("is_favorite", false)
        )
    }
}

data class Album(
    val bucketId: String,
    val bucketName: String,
    val coverUri: String,
    val coverMediaType: String,
    val mediaCount: Long,
    val latestModifiedSec: Long,
    val isHidden: Boolean
) {
    companion object {
        fun fromJson(obj: JSONObject): Album = Album(
            bucketId = obj.getString("bucket_id"),
            bucketName = obj.getString("bucket_name"),
            coverUri = obj.getString("cover_uri"),
            coverMediaType = obj.optString("cover_media_type", "image"),
            mediaCount = obj.getLong("media_count"),
            latestModifiedSec = obj.getLong("latest_modified_sec"),
            isHidden = obj.optBoolean("is_hidden", false)
        )
    }
}

data class CacheStats(
    val memoryEntries: Int,
    val memoryBytes: Long,
    val diskBytes: Long
)

data class StorageInfo(
    val totalItems: Long,
    val totalBytes: Long
)

object RustEngine {
    init {
        System.loadLibrary("fotos_engine")
    }

    @JvmStatic
    external fun nativeInit(dbDir: String, cacheDir: String): Boolean

    @JvmStatic
    external fun nativeSyncMedia(itemsJson: String, pruneMissing: Boolean): String

    @JvmStatic
    external fun nativeGetMediaPaginated(
        offset: Long,
        limit: Long,
        includeHidden: Boolean,
        sortAsc: Boolean
    ): String

    @JvmStatic
    external fun nativeGetAlbumMediaPaginated(
        bucketId: String,
        offset: Long,
        limit: Long,
        sortAsc: Boolean
    ): String

    @JvmStatic
    external fun nativeGetAlbums(includeHidden: Boolean, sortMode: Int): String

    @JvmStatic
    external fun nativeSearchMedia(query: String, limit: Long): String

    @JvmStatic
    external fun nativeDeleteMedia(id: Long): Boolean

    @JvmStatic
    external fun nativeToggleFavorite(id: Long): Boolean

    @JvmStatic
    external fun nativeSetAlbumHidden(bucketId: String, bucketName: String, hidden: Boolean): Boolean

    @JvmStatic
    external fun nativeGetHiddenAlbums(): String

    @JvmStatic
    external fun nativeGetCachedThumbnail(id: Long): ByteArray?

    @JvmStatic
    external fun nativePutCachedThumbnail(id: Long, data: ByteArray)

    @JvmStatic
    external fun nativeClearCache(): Boolean

    @JvmStatic
    external fun nativeGetCacheStats(): String

    @JvmStatic
    external fun nativeGetStorageInfo(): String

    fun syncMedia(items: List<MediaItem>, pruneMissing: Boolean): JSONObject {
        val arr = JSONArray()
        for (item in items) {
            arr.put(item.toJson())
        }
        val res = nativeSyncMedia(arr.toString(), pruneMissing)
        return JSONObject(res)
    }

    fun getMediaPaginated(
        offset: Long,
        limit: Long,
        includeHidden: Boolean = false,
        sortAsc: Boolean = false
    ): List<MediaItem> = parseMediaList(nativeGetMediaPaginated(offset, limit, includeHidden, sortAsc))

    fun getAlbumMediaPaginated(
        bucketId: String,
        offset: Long,
        limit: Long,
        sortAsc: Boolean = false
    ): List<MediaItem> = parseMediaList(nativeGetAlbumMediaPaginated(bucketId, offset, limit, sortAsc))

    fun getAlbums(includeHidden: Boolean = false, sortMode: Int = 0): List<Album> {
        val arr = JSONArray(nativeGetAlbums(includeHidden, sortMode))
        val out = ArrayList<Album>(arr.length())
        for (i in 0 until arr.length()) {
            out.add(Album.fromJson(arr.getJSONObject(i)))
        }
        return out
    }

    fun searchMedia(query: String, limit: Long = 200): List<MediaItem> =
        parseMediaList(nativeSearchMedia(query, limit))

    fun getHiddenAlbums(): List<Pair<String, String>> {
        val arr = JSONArray(nativeGetHiddenAlbums())
        val out = ArrayList<Pair<String, String>>(arr.length())
        for (i in 0 until arr.length()) {
            val pair = arr.getJSONArray(i)
            out.add(pair.getString(0) to pair.getString(1))
        }
        return out
    }

    fun getCacheStats(): CacheStats {
        val obj = JSONObject(nativeGetCacheStats())
        return CacheStats(
            memoryEntries = obj.optInt("memory_entries", 0),
            memoryBytes = obj.optLong("memory_bytes", 0L),
            diskBytes = obj.optLong("disk_bytes", 0L)
        )
    }

    fun getStorageInfo(): StorageInfo {
        val obj = JSONObject(nativeGetStorageInfo())
        return StorageInfo(
            totalItems = obj.optLong("total_items", 0L),
            totalBytes = obj.optLong("total_bytes", 0L)
        )
    }

    private fun parseMediaList(json: String): List<MediaItem> {
        val arr = JSONArray(json)
        val out = ArrayList<MediaItem>(arr.length())
        for (i in 0 until arr.length()) {
            out.add(MediaItem.fromJson(arr.getJSONObject(i)))
        }
        return out
    }
}
