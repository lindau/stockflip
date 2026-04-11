package com.stockflip

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.stockflip.ui.SwipeToDeleteCallback
import com.stockflip.ui.WatchItemSkeletonList
import com.stockflip.ui.theme.StockFlipTheme
import com.google.android.material.snackbar.Snackbar
import com.stockflip.databinding.FragmentPairsBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PairsFragment : Fragment() {

    private var _binding: FragmentPairsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext: Context = requireContext().applicationContext
                val database = StockPairDatabase.getDatabase(appContext)
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(
                    database.stockPairDao(),
                    database.watchItemDao(),
                    YahooFinanceService
                ) as T
            }
        }
    }

    private lateinit var groupedAdapter: GroupedWatchItemAdapter
    private var pendingDeleteSnackbar: Snackbar? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPairsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.skeletonLoadingView.setContent {
            StockFlipTheme {
                WatchItemSkeletonList(count = 4)
            }
        }
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        groupedAdapter = GroupedWatchItemAdapter(
            onToggleActive = { watchItem ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.toggleWatchItemActive(watchItem, !watchItem.isActive)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte uppdatera bevakning", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onReactivate = { watchItem ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.reactivateWatchItem(watchItem)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte återaktivera bevakning", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDeleteClick = { watchItem ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.deleteWatchItem(watchItem)
                        Toast.makeText(requireContext(), R.string.alert_deleted, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte ta bort aktiepar", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onEditClick = { watchItem ->
                (requireActivity() as? MainActivity)?.showEditDialogFromPairs(watchItem)
            },
            onItemClick = { watchItem ->
                (requireActivity() as? MainActivity)?.navigateToPairDetailFromPairs(watchItem.id)
            }
        )

        binding.pairsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = groupedAdapter
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    viewModel.refreshWatchItems()
                } catch (e: Exception) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        val swipeCallback = SwipeToDeleteCallback(
            context = requireContext(),
            canSwipe = { position ->
                groupedAdapter.currentList.getOrNull(position) is GroupedListItem.WatchItemWrapper
            },
            onSwiped = { position ->
                val listItem = groupedAdapter.currentList.getOrNull(position) as? GroupedListItem.WatchItemWrapper
                    ?: run {
                        groupedAdapter.notifyItemChanged(position)
                        return@SwipeToDeleteCallback
                    }
                groupedAdapter.notifyItemChanged(position)
                val itemToDelete = listItem.item
                pendingDeleteSnackbar?.dismiss()
                val snackbar = Snackbar.make(binding.root, R.string.alert_deleted, Snackbar.LENGTH_LONG)
                snackbar.setAction(R.string.alert_undo) { /* Behåll — item tas inte bort */ }
                snackbar.addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (event != DISMISS_EVENT_ACTION) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    viewModel.deleteWatchItem(itemToDelete)
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), e.message ?: "Kunde inte ta bort aktiepar", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                })
                snackbar.show()
                pendingDeleteSnackbar = snackbar
            },
            onSwipedRight = { position ->
                val listItem = groupedAdapter.currentList.getOrNull(position) as? GroupedListItem.WatchItemWrapper
                    ?: return@SwipeToDeleteCallback
                groupedAdapter.notifyItemChanged(position)
                val item = listItem.item
                binding.pairsRecyclerView.postDelayed({
                    (requireActivity() as? MainActivity)?.navigateToPairDetailFromPairs(item.id)
                }, 120)
            }
        )
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.pairsRecyclerView)
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.watchItemUiState,
                    viewModel.pairsSortMode
                ) { state, sortMode ->
                    Pair(state, sortMode)
                }.collect { (state, sortMode) ->
                    when (state) {
                        is UiState.Loading -> {
                            if (!binding.swipeRefreshLayout.isRefreshing) {
                                binding.skeletonLoadingView.visibility = View.VISIBLE
                            }
                            binding.sortModeLabel.visibility = View.GONE
                        }
                        is UiState.Success -> {
                            binding.skeletonLoadingView.visibility = View.GONE
                            binding.swipeRefreshLayout.isRefreshing = false
                            val pairs = state.data.filter { it.item.watchType is WatchType.PricePair }
                            TriggerSeenTracker.markAllSeen(pairs.map { it.item })
                            groupedAdapter.submitGroupedList(pairs, sortMode)
                            binding.emptyStateContainer.visibility = if (pairs.isEmpty()) View.VISIBLE else View.GONE
                            when (sortMode) {
                                SortHelper.SortMode.ALPHABETICAL -> {
                                    binding.sortModeLabel.text = getString(R.string.sort_label_alphabetical)
                                    binding.sortModeLabel.visibility = View.VISIBLE
                                }
                                SortHelper.SortMode.ADDITION_ORDER -> {
                                    binding.sortModeLabel.visibility = View.GONE
                                }
                            }
                        }
                        is UiState.Error -> {
                            binding.skeletonLoadingView.visibility = View.GONE
                            binding.swipeRefreshLayout.isRefreshing = false
                            binding.emptyStateContainer.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        pendingDeleteSnackbar?.dismiss()
        pendingDeleteSnackbar = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG: String = "PairsFragment"
    }
}
