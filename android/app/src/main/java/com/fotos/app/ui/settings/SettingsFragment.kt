package com.fotos.app.ui.settings

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.fotos.app.BuildConfig
import com.fotos.app.FotosApp
import com.fotos.app.databinding.FragmentSettingsBinding
import com.fotos.app.engine.RustEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvAboutVersion.text = "Fotos v${BuildConfig.VERSION_NAME} • Offline Rust Engine"

        binding.btnTheme.setOnClickListener { showThemeDialog() }
        binding.btnSortOrder.setOnClickListener { showSortDialog() }
        binding.btnClearCache.setOnClickListener { clearCache() }
        binding.btnHiddenAlbums.setOnClickListener { showHiddenAlbumsDialog() }

        refreshStats()
    }

    private fun refreshStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (cacheStats, storageInfo) = withContext(Dispatchers.IO) {
                RustEngine.getCacheStats() to RustEngine.getStorageInfo()
            }
            if (_binding != null) {
                val ctx = requireContext()
                val cacheSize = Formatter.formatShortFileSize(ctx, cacheStats.diskBytes + cacheStats.memoryBytes)
                val totalSize = Formatter.formatShortFileSize(ctx, storageInfo.totalBytes)
                binding.tvCacheInfo.text = "Cache: $cacheSize (${cacheStats.memoryEntries} in RAM)"
                binding.tvStorageInfo.text = "${storageInfo.totalItems} items • $totalSize"
            }
        }
    }

    private fun showThemeDialog() {
        val options = arrayOf("Dark (Default)", "Light", "Follow System")
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Theme")
            .setItems(options) { _, which ->
                FotosApp.prefs(requireContext()).edit()
                    .putInt(FotosApp.KEY_THEME, modes[which])
                    .apply()
                AppCompatDelegate.setDefaultNightMode(modes[which])
            }
            .show()
    }

    private fun showSortDialog() {
        val options = arrayOf("Newest first", "Oldest first")
        AlertDialog.Builder(requireContext())
            .setTitle("Photo Sort Order")
            .setItems(options) { _, which ->
                FotosApp.prefs(requireContext()).edit()
                    .putBoolean(FotosApp.KEY_SORT_ASC, which == 1)
                    .apply()
            }
            .show()
    }

    private fun clearCache() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { RustEngine.nativeClearCache() }
            refreshStats()
        }
    }

    private fun showHiddenAlbumsDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val hidden = withContext(Dispatchers.IO) { RustEngine.getHiddenAlbums() }
            if (_binding == null) return@launch
            if (hidden.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Hidden Albums")
                    .setMessage("No hidden albums. Long-press any album to hide it.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }
            val names = hidden.map { "${it.second} (tap to unhide)" }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Hidden Albums")
                .setItems(names) { _, which ->
                    val (bid, bname) = hidden[which]
                    viewLifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            RustEngine.nativeSetAlbumHidden(bid, bname, false)
                        }
                    }
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
