package com.fotos.app.ui.albums

import android.net.Uri
import android.os.CancellationSignal
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fotos.app.data.ThumbnailLoader
import com.fotos.app.databinding.ItemAlbumBinding
import com.fotos.app.engine.Album
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AlbumsAdapter(
    private val scope: CoroutineScope,
    private val onClick: (Album) -> Unit,
    private val onLongClick: (Album) -> Unit
) : RecyclerView.Adapter<AlbumsAdapter.AlbumVH>() {

    private val albums = ArrayList<Album>()

    fun submitList(list: List<Album>) {
        albums.clear()
        albums.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = albums.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumVH {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumVH(binding)
    }

    override fun onBindViewHolder(holder: AlbumVH, position: Int) {
        holder.bind(albums[position])
    }

    override fun onViewRecycled(holder: AlbumVH) {
        holder.cancelLoad()
        super.onViewRecycled(holder)
    }

    inner class AlbumVH(private val binding: ItemAlbumBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var job: Job? = null
        private var signal: CancellationSignal? = null

        fun cancelLoad() {
            job?.cancel()
            signal?.cancel()
            job = null
            signal = null
        }

        fun bind(album: Album) {
            cancelLoad()
            binding.tvAlbumName.text = album.bucketName
            binding.tvAlbumCount.text = album.mediaCount.toString()
            binding.ivCover.setImageDrawable(null)

            val coverId = Uri.parse(album.coverUri).lastPathSegment?.toLongOrNull() ?: 0L
            if (coverId > 0L) {
                val sig = CancellationSignal()
                signal = sig
                job = scope.launch {
                    val bmp = ThumbnailLoader.loadThumbnail(
                        binding.root.context,
                        coverId,
                        album.coverUri,
                        sig
                    )
                    if (bmp != null && !sig.isCanceled) {
                        binding.ivCover.setImageBitmap(bmp)
                    }
                }
            }

            binding.root.setOnClickListener { onClick(album) }
            binding.root.setOnLongClickListener {
                onLongClick(album)
                true
            }
        }
    }
}
