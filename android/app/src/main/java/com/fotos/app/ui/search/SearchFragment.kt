package com.fotos.app.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.fotos.app.databinding.FragmentSearchBinding
import com.fotos.app.engine.RustEngine
import com.fotos.app.ui.photos.PhotosAdapter
import com.fotos.app.ui.viewer.ViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PhotosAdapter
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PhotosAdapter(viewLifecycleOwner.lifecycleScope) { item, allItems ->
            val index = allItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            ViewerActivity.launch(requireContext(), allItems, index)
        }

        val glm = GridLayoutManager(requireContext(), 4)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.getItemViewType(position) == PhotosAdapter.TYPE_HEADER) 4 else 1
            }
        }
        binding.recyclerSearch.layoutManager = glm
        binding.recyclerSearch.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(150)
                    val results = withContext(Dispatchers.IO) {
                        RustEngine.searchMedia(query, 200L)
                    }
                    if (_binding != null) {
                        adapter.submitMedia(results, append = false)
                        binding.tvSearchHint.visibility =
                            if (results.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
