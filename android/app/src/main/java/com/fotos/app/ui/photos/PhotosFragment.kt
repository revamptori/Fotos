package com.fotos.app.ui.photos

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fotos.app.FotosApp
import com.fotos.app.databinding.FragmentPhotosBinding
import com.fotos.app.engine.MediaItem
import com.fotos.app.engine.RustEngine
import com.fotos.app.ui.MainActivity
import com.fotos.app.ui.viewer.ViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotosFragment : Fragment(), MainActivity.Refreshable {

    private var _binding: FragmentPhotosBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PhotosAdapter
    private var currentOffset = 0L
    private var isLoading = false
    private var hasMore = true
    private val pageSize = 120L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PhotosAdapter(viewLifecycleOwner.lifecycleScope) { item, allItems ->
            val index = allItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            ViewerActivity.launch(requireContext(), allItems, index)
        }

        val spanCount = 4
        val glm = GridLayoutManager(requireContext(), spanCount)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.getItemViewType(position) == PhotosAdapter.TYPE_HEADER) spanCount else 1
            }
        }

        binding.recyclerPhotos.layoutManager = glm
        binding.recyclerPhotos.adapter = adapter
        binding.recyclerPhotos.setHasFixedSize(true)

        binding.recyclerPhotos.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !isLoading && hasMore) {
                    val lastVisible = glm.findLastVisibleItemPosition()
                    if (lastVisible >= adapter.itemCount - 20) {
                        loadPage(append = true)
                    }
                }
            }
        })

        refresh()
    }

    override fun refresh() {
        if (_binding == null) return
        currentOffset = 0L
        hasMore = true
        loadPage(append = false)
    }

    private fun loadPage(append: Boolean) {
        if (isLoading) return
        isLoading = true
        val sortAsc = FotosApp.prefs(requireContext()).getBoolean(FotosApp.KEY_SORT_ASC, false)

        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                RustEngine.getMediaPaginated(currentOffset, pageSize, includeHidden = false, sortAsc = sortAsc)
            }
            if (_binding != null) {
                adapter.submitMedia(items, append = append)
                currentOffset += items.size
                hasMore = items.size.toLong() == pageSize
                binding.tvEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
            }
            isLoading = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
