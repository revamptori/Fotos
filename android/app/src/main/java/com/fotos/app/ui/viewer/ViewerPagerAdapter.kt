package com.fotos.app.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fotos.app.data.ThumbnailLoader
import com.fotos.app.databinding.ItemViewerPageBinding
import com.fotos.app.engine.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewerPagerAdapter(
    private val scope: CoroutineScope,
    private val items: List<MediaItem>,
    private val onTap: () -> Unit,
    private val onPlayVideo: (MediaItem) -> Unit
) : RecyclerView.Adapter<ViewerPagerAdapter.PageVH>() {

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val binding = ItemViewerPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageVH(binding)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewRecycled(holder: PageVH) {
        holder.cancel()
        super.onViewRecycled(holder)
    }

    inner class PageVH(private val binding: ItemViewerPageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var job: Job? = null

        fun cancel() {
            job?.cancel()
            job = null
        }

        fun bind(item: MediaItem) {
            cancel()
            binding.ivFull.setImageDrawable(null)
            binding.ivFull.onSingleTap = onTap
            binding.btnPlayVideo.visibility = if (item.isVideo) View.VISIBLE else View.GONE
            binding.btnPlayVideo.setOnClickListener { onPlayVideo(item) }

            job = scope.launch {
                val bmp = if (item.isVideo) {
                    ThumbnailLoader.loadThumbnail(binding.root.context, item.id, item.uri)
                } else {
                    decodeSampledBitmap(binding.root.context, Uri.parse(item.uri), 2048, 2048)
                }
                if (bmp != null) {
                    binding.ivFull.setImageBitmap(bmp)
                    binding.ivFull.resetZoom()
                }
            }
        }

        private suspend fun decodeSampledBitmap(
            context: Context,
            uri: Uri,
            reqW: Int,
            reqH: Int
        ): Bitmap? = withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                var inSampleSize = 1
                if (opts.outHeight > reqH || opts.outWidth > reqW) {
                    val halfH = opts.outHeight / 2
                    val halfW = opts.outWidth / 2
                    while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                        inSampleSize *= 2
                    }
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
