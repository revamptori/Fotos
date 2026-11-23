package com.fotos.app.data

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.fotos.app.engine.MediaItem
import com.fotos.app.engine.RustEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreSync {

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasMediaPermissions(context: Context): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun syncAll(context: Context): Int = withContext(Dispatchers.IO) {
        if (!hasMediaPermissions(context)) return@withContext 0

        val items = ArrayList<MediaItem>(512)
        queryCollection(
            context = context,
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            isVideo = false,
            out = items
        )
        queryCollection(
            context = context,
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            isVideo = true,
            out = items
        )

        RustEngine.syncMedia(items, pruneMissing = true)
        items.size
    }

    private fun queryCollection(
        context: Context,
        collection: Uri,
        isVideo: Boolean,
        out: MutableList<MediaItem>
    ) {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.BUCKET_ID)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            if (isVideo) {
                add(MediaStore.Video.VideoColumns.DURATION)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val durCol = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                val name = cursor.getString(nameCol) ?: "untitled"
                val bucketId = cursor.getString(bucketIdCol) ?: "0"
                val bucketName = cursor.getString(bucketNameCol) ?: "Internal"
                val mime = cursor.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*"
                val size = cursor.getLong(sizeCol)
                val width = cursor.getInt(widthCol)
                val height = cursor.getInt(heightCol)
                val added = cursor.getLong(addedCol)
                val modified = cursor.getLong(modCol)
                val duration = if (durCol >= 0) cursor.getLong(durCol) else 0L

                out.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        displayName = name,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        mediaType = if (isVideo) "video" else "image",
                        mimeType = mime,
                        sizeBytes = size,
                        width = width,
                        height = height,
                        durationMs = duration,
                        dateAddedSec = added,
                        dateModifiedSec = modified,
                        isFavorite = false
                    )
                )
            }
        }
    }

    fun createDeleteRequest(context: Context, uri: Uri): PendingIntent {
        return MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
    }

    fun shareMedia(context: Context, item: MediaItem) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, item.displayName))
    }

    fun openInExternalApp(context: Context, item: MediaItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(item.uri), item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, item.displayName))
    }
}
