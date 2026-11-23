package com.fotos.app.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.fotos.app.data.MediaStoreSync
import com.fotos.app.databinding.ActivityViewerBinding
import com.fotos.app.engine.MediaItem
import com.fotos.app.engine.RustEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewerBinding
    private var items = ArrayList<MediaItem>()
    private var chromeVisible = true
    private var pendingDeleteId: Long = -1L

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingDeleteId > 0) {
            val id = pendingDeleteId
            pendingDeleteId = -1L
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { RustEngine.nativeDeleteMedia(id) }
                finish()
            }
        }
    }

    companion object {
        private var cachedItems: List<MediaItem> = emptyList()
        private const val EXTRA_INDEX = "index"

        fun launch(context: Context, list: List<MediaItem>, index: Int) {
            cachedItems = list
            val intent = Intent(context, ViewerActivity::class.java).apply {
                putExtra(EXTRA_INDEX, index)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        items.addAll(cachedItems)

        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (items.size - 1).coerceAtLeast(0))

        val adapter = ViewerPagerAdapter(
            scope = lifecycleScope,
            items = items,
            onTap = { toggleChrome() },
            onPlayVideo = { item -> VideoPlayerActivity.launch(this, item.uri, item.displayName) }
        )

        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(startIndex, false)
        updateTitle(startIndex)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTitle(position)
            }
        })

        binding.btnBack.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener {
            currentItem()?.let { MediaStoreSync.shareMedia(this, it) }
        }
        binding.btnInfo.setOnClickListener {
            currentItem()?.let { showInfoDialog(it) }
        }
        binding.btnFavorite.setOnClickListener {
            currentItem()?.let { item ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { RustEngine.nativeToggleFavorite(item.id) }
                }
            }
        }
        binding.btnOpenWith.setOnClickListener {
            currentItem()?.let { MediaStoreSync.openInExternalApp(this, it) }
        }
        binding.btnDelete.setOnClickListener {
            currentItem()?.let { item ->
                pendingDeleteId = item.id
                val pending = MediaStoreSync.createDeleteRequest(this, Uri.parse(item.uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            }
        }
    }

    private fun currentItem(): MediaItem? {
        val idx = binding.viewPager.currentItem
        return items.getOrNull(idx)
    }

    private fun updateTitle(position: Int) {
        items.getOrNull(position)?.let {
            binding.tvTitle.text = it.displayName
        }
    }

    private fun toggleChrome() {
        chromeVisible = !chromeVisible
        val vis = if (chromeVisible) View.VISIBLE else View.GONE
        binding.topBar.visibility = vis
        binding.bottomBar.visibility = vis

        val controller = WindowInsetsControllerCompat(window, binding.root)
        if (chromeVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showInfoDialog(item: MediaItem) {
        val sizeStr = Formatter.formatShortFileSize(this, item.sizeBytes)
        val details = StringBuilder()
            .append("Name: ${item.displayName}\n")
            .append("Album: ${item.bucketName}\n")
            .append("Type: ${item.mimeType}\n")
            .append("Dimensions: ${item.width} × ${item.height}\n")
            .append("Size: $sizeStr\n")

        if (!item.isVideo) {
            try {
                contentResolver.openInputStream(Uri.parse(item.uri))?.use { stream ->
                    val exif = ExifInterface(stream)
                    exif.getAttribute(ExifInterface.TAG_MODEL)?.let { details.append("Camera: $it\n") }
                    exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { details.append("Aperture: f/$it\n") }
                    exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { details.append("Exposure: ${it}s\n") }
                    exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { details.append("ISO: $it\n") }
                }
            } catch (_: Exception) {}
        }

        AlertDialog.Builder(this)
            .setTitle("Media Info")
            .setMessage(details.toString().trim())
            .setPositiveButton("OK", null)
            .show()
    }
}
