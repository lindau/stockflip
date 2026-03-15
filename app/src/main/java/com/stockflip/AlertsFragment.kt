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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.stockflip.databinding.FragmentAlertsBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
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
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
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
                showDeleteConfirmation(watchItem)
            },
            onEditClick = { watchItem ->
                (requireActivity() as? MainActivity)?.showEditDialogFromAlerts(watchItem)
            },
            onItemClick = { watchItem ->
                val symbol = watchItem.ticker ?: watchItem.ticker1 ?: return@GroupedWatchItemAdapter
                (requireActivity() as? MainActivity)?.navigateToStockDetailFromAlerts(
                    symbol = symbol,
                    companyName = watchItem.companyName
                )
            }
        )

        binding.alertsRecyclerView.apply {
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
                // Återställ ItemTouchHelper-state direkt — DiffUtil animerar bort raden när Room uppdaterar
                groupedAdapter.notifyItemChanged(position)
                val itemToDelete = listItem.item
                // Dismiss any pending snackbar from a previous swipe before showing the new one
                pendingDeleteSnackbar?.dismiss()
                val snackbar = Snackbar.make(binding.root, R.string.alert_deleted, Snackbar.LENGTH_LONG)
                snackbar.setAction(R.string.alert_undo) { /* Do nothing — item stays */ }
                snackbar.addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (event != DISMISS_EVENT_ACTION) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    viewModel.deleteWatchItem(itemToDelete)
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), e.message ?: "Kunde inte ta bort bevakning", Toast.LENGTH_LONG).show()
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
                // Snap row back first, then navigate after animation completes
                groupedAdapter.notifyItemChanged(position)
                val item = listItem.item
                val symbol = item.ticker ?: item.ticker1 ?: return@SwipeToDeleteCallback
                val companyName = item.companyName
                binding.alertsRecyclerView.postDelayed({
                    (requireActivity() as? MainActivity)?.navigateToStockDetailFromAlerts(
                        symbol = symbol,
                        companyName = companyName
                    )
                }, 120)
            }
        )
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.alertsRecyclerView)
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.watchItemUiState,
                    viewModel.alertSortMode
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
                            val items = state.data
                            groupedAdapter.submitGroupedList(items, sortMode)
                            binding.emptyStateContainer.visibility = if (items.isEmpty()) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }
                            // Update sort mode label
                            when (sortMode) {
                                SortHelper.SortMode.ALPHABETICAL -> {
                                    binding.sortModeLabel.text = "Sorterat: Bokstavsordning"
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
                            binding.emptyStateText.text = state.message
                            binding.sortModeLabel.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmation(watchItem: WatchItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.alert_delete_title)
            .setMessage(R.string.alert_delete_message)
            .setPositiveButton(R.string.alert_delete_positive) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.deleteWatchItem(watchItem)
                        Toast.makeText(requireContext(), R.string.alert_deleted, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte ta bort bevakning", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.alert_delete_negative, null)
            .show()
    }

    override fun onDestroyView() {
        pendingDeleteSnackbar?.dismiss()
        pendingDeleteSnackbar = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG: String = "AlertsFragment"
    }
}

