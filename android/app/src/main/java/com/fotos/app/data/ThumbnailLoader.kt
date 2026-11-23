package com.fotos.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import com.fotos.app.engine.RustEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ThumbnailLoader {
    private val THUMB_SIZE = Size(320, 320)

    suspend fun loadThumbnail(
        context: Context,
        id: Long,
        uriString: String,
        signal: CancellationSignal? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (signal?.isCanceled == true) return@withContext null

        val cached = RustEngine.nativeGetCachedThumbnail(id)
        if (cached != null && cached.isNotEmpty()) {
            return@withContext BitmapFactory.decodeByteArray(cached, 0, cached.size)
        }

        if (signal?.isCanceled == true) return@withContext null

        try {
            val uri = Uri.parse(uriString)
            val bitmap = context.contentResolver.loadThumbnail(uri, THUMB_SIZE, signal)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            RustEngine.nativePutCachedThumbnail(id, out.toByteArray())
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
