package com.fotos.app

import com.fotos.app.engine.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemTest {
    @Test
    fun mediaItemJsonRoundTrip() {
        val item = MediaItem(
            id = 42L,
            uri = "content://media/external/images/media/42",
            displayName = "photo.jpg",
            bucketId = "100",
            bucketName = "Camera",
            mediaType = "image",
            mimeType = "image/jpeg",
            sizeBytes = 2048L,
            width = 1920,
            height = 1080,
            durationMs = 0L,
            dateAddedSec = 1700000000L,
            dateModifiedSec = 1700000000L,
            isFavorite = true
        )
        val parsed = MediaItem.fromJson(item.toJson())
        assertEquals(item.id, parsed.id)
        assertEquals(item.displayName, parsed.displayName)
        assertTrue(parsed.isFavorite)
    }
}
