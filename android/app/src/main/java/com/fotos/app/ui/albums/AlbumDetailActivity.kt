package com.fotos.app.ui.albums

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.fotos.app.FotosApp
import com.fotos.app.databinding.ActivityAlbumDetailBinding
import com.fotos.app.engine.RustEngine
import com.fotos.app.ui.photos.PhotosAdapter
import com.fotos.app.ui.viewer.ViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlbumDetailBinding
    private lateinit var adapter: PhotosAdapter

    companion object {
        const val EXTRA_BUCKET_ID = "bucket_id"
        const val EXTRA_BUCKET_NAME = "bucket_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlbumDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bucketId = intent.getStringExtra(EXTRA_BUCKET_ID) ?: ""
        val bucketName = intent.getStringExtra(EXTRA_BUCKET_NAME) ?: "Album"

        binding.toolbar.title = bucketName
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = PhotosAdapter(lifecycleScope) { item, allItems ->
            val index = allItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            ViewerActivity.launch(this, allItems, index)
        }

        val glm = GridLayoutManager(this, 4)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.getItemViewType(position) == PhotosAdapter.TYPE_HEADER) 4 else 1
            }
        }
        binding.recyclerAlbumMedia.layoutManager = glm
        binding.recyclerAlbumMedia.adapter = adapter

        val sortAsc = FotosApp.prefs(this).getBoolean(FotosApp.KEY_SORT_ASC, false)
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                RustEngine.getAlbumMediaPaginated(bucketId, 0L, 500L, sortAsc)
            }
            adapter.submitMedia(items, append = false)
        }
    }
}
