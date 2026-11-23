package com.fotos.app.ui.albums

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.fotos.app.FotosApp
import com.fotos.app.databinding.FragmentAlbumsBinding
import com.fotos.app.engine.Album
import com.fotos.app.engine.RustEngine
import com.fotos.app.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumsFragment : Fragment(), MainActivity.Refreshable {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AlbumsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = AlbumsAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            onClick = { album ->
                val intent = Intent(requireContext(), AlbumDetailActivity::class.java).apply {
                    putExtra(AlbumDetailActivity.EXTRA_BUCKET_ID, album.bucketId)
                    putExtra(AlbumDetailActivity.EXTRA_BUCKET_NAME, album.bucketName)
                }
                startActivity(intent)
            },
            onLongClick = { album ->
                showHideAlbumDialog(album)
            }
        )

        binding.recyclerAlbums.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerAlbums.adapter = adapter
        refresh()
    }

    private fun showHideAlbumDialog(album: Album) {
        AlertDialog.Builder(requireContext())
            .setTitle(album.bucketName)
            .setMessage("Hide this album from Photos and Albums?")
            .setPositiveButton("Hide") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        RustEngine.nativeSetAlbumHidden(album.bucketId, album.bucketName, true)
                    }
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun refresh() {
        if (_binding == null) return
        val sortMode = FotosApp.prefs(requireContext()).getInt(FotosApp.KEY_ALBUM_SORT, 0)
        viewLifecycleOwner.lifecycleScope.launch {
            val albums = withContext(Dispatchers.IO) {
                RustEngine.getAlbums(includeHidden = false, sortMode = sortMode)
            }
            if (_binding != null) {
                adapter.submitList(albums)
                binding.tvEmptyAlbums.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
