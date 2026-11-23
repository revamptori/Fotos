package com.fotos.app.ui.photos

import android.os.CancellationSignal
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fotos.app.data.ThumbnailLoader
import com.fotos.app.databinding.ItemDateHeaderBinding
import com.fotos.app.databinding.ItemMediaGridBinding
import com.fotos.app.engine.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class GalleryRow {
    data class DateHeader(val title: String) : GalleryRow()
    data class Media(val item: MediaItem) : GalleryRow()
}

class PhotosAdapter(
    private val scope: CoroutineScope,
    private val onMediaClick: (MediaItem, List<MediaItem>) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = ArrayList<GalleryRow>()
    private val mediaItems = ArrayList<MediaItem>()
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_MEDIA = 1
    }

    fun submitMedia(items: List<MediaItem>, append: Boolean = false) {
        if (!append) {
            rows.clear()
            mediaItems.clear()
        }
        var lastDate = (rows.lastOrNull() as? GalleryRow.DateHeader)?.title
        if (lastDate == null && mediaItems.isNotEmpty()) {
            lastDate = dateFormat.format(Date(mediaItems.last().dateModifiedSec * 1000L))
        }

        for (item in items) {
            mediaItems.add(item)
            val dateStr = dateFormat.format(Date(item.dateModifiedSec * 1000L))
            if (dateStr != lastDate) {
                rows.add(GalleryRow.DateHeader(dateStr))
                lastDate = dateStr
            }
            rows.add(GalleryRow.Media(item))
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is GalleryRow.DateHeader -> TYPE_HEADER
        is GalleryRow.Media -> TYPE_MEDIA
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemDateHeaderBinding.inflate(inflater, parent, false))
        } else {
            MediaVH(ItemMediaGridBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is GalleryRow.DateHeader -> (holder as HeaderVH).bind(row.title)
            is GalleryRow.Media -> (holder as MediaVH).bind(row.item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is MediaVH) {
            holder.cancelLoad()
        }
        super.onViewRecycled(holder)
    }

    inner class HeaderVH(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.tvDateHeader.text = title
        }
    }

    inner class MediaVH(private val binding: ItemMediaGridBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var loadJob: Job? = null
        private var signal: CancellationSignal? = null

        fun cancelLoad() {
            loadJob?.cancel()
            signal?.cancel()
            loadJob = null
            signal = null
        }

        fun bind(item: MediaItem) {
            cancelLoad()
            binding.ivThumb.setImageDrawable(null)
            binding.tvDuration.visibility = if (item.isVideo) View.VISIBLE else View.GONE
            if (item.isVideo) {
                val secs = item.durationMs / 1000
                binding.tvDuration.text = String.format(Locale.US, "%d:%02d", secs / 60, secs % 60)
            }
            binding.ivFavorite.visibility = if (item.isFavorite) View.VISIBLE else View.GONE

            val sig = CancellationSignal()
            signal = sig
            loadJob = scope.launch {
                val bmp = ThumbnailLoader.loadThumbnail(
                    binding.root.context,
                    item.id,
                    item.uri,
                    sig
                )
                if (bmp != null && !sig.isCanceled) {
                    binding.ivThumb.setImageBitmap(bmp)
                }
            }

            binding.root.setOnClickListener {
                onMediaClick(item, mediaItems)
            }
        }
    }
}
