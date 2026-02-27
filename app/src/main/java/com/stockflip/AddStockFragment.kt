package com.stockflip

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stockflip.databinding.FragmentAddStockBinding
import com.stockflip.databinding.ItemStockSearchResultBinding
import com.stockflip.repository.SearchState
import com.stockflip.repository.StockRepository
import com.stockflip.viewmodel.StockSearchViewModel
import kotlinx.coroutines.launch

class AddStockFragment : Fragment() {

    private var _binding: FragmentAddStockBinding? = null
    private val binding get() = _binding!!

    private val searchViewModel: StockSearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StockSearchViewModel(StockRepository()) as T
            }
        }
    }

    private lateinit var adapter: StockSearchResultAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddStockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        observeSearchState()
    }

    private fun setupRecyclerView() {
        adapter = StockSearchResultAdapter { result ->
            navigateToStockDetail(result)
        }
        binding.resultsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.resultsRecyclerView.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchInput.setOnEditorActionListener { _, _, _ ->
            performSearch(binding.searchInput.text?.toString().orEmpty())
            true
        }

        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }

    private fun performSearch(query: String) {
        searchViewModel.search(query, includeCrypto = true)
    }

    private fun observeSearchState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchViewModel.searchState.collect { state ->
                    when (state) {
                        is SearchState.Loading -> {
                            binding.searchProgressBar.visibility = View.VISIBLE
                            binding.emptyText.visibility = View.GONE
                        }

                        is SearchState.Success -> {
                            binding.searchProgressBar.visibility = View.GONE
                            adapter.submitList(state.results)
                            binding.emptyText.visibility =
                                if (state.results.isEmpty()) View.VISIBLE else View.GONE
                        }

                        is SearchState.Error -> {
                            binding.searchProgressBar.visibility = View.GONE
                            binding.emptyText.visibility = View.VISIBLE
                            binding.emptyText.text = getString(R.string.add_stock_error)
                            Toast.makeText(
                                requireContext(),
                                state.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun navigateToStockDetail(result: StockSearchResult) {
        val fragment = StockDetailFragment.newInstance(result.symbol, result.name)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack("stock_detail")
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class StockSearchResultAdapter(
        private val onItemClick: (StockSearchResult) -> Unit
    ) : ListAdapter<StockSearchResult, StockSearchResultAdapter.ViewHolder>(DiffCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemStockSearchResultBinding.inflate(inflater, parent, false)
            return ViewHolder(binding, onItemClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class ViewHolder(
            private val binding: ItemStockSearchResultBinding,
            private val onItemClick: (StockSearchResult) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: StockSearchResult) {
                binding.nameText.text = item.name
                binding.symbolText.text = item.symbol
                binding.root.setOnClickListener {
                    onItemClick(item)
                }
            }
        }

        private object DiffCallback : DiffUtil.ItemCallback<StockSearchResult>() {
            override fun areItemsTheSame(
                oldItem: StockSearchResult,
                newItem: StockSearchResult
            ): Boolean = oldItem.symbol == newItem.symbol

            override fun areContentsTheSame(
                oldItem: StockSearchResult,
                newItem: StockSearchResult
            ): Boolean = oldItem == newItem
        }
    }
}

