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

    private enum class AlertsFilter {
        ALL,
        ACTIVE,
        TRIGGERED,
        PRICE,
        METRICS,
        PAIRS,
    }

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
    private var currentFilter: AlertsFilter = AlertsFilter.ALL
    private var latestItems: List<WatchItemUiState> = emptyList()
    private var latestSortMode: SortHelper.SortMode = SortHelper.SortMode.ADDITION_ORDER
    private val selectedRuleIds: MutableSet<Int> = mutableSetOf()
    private var selectionMode: Boolean = false

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
        setupFilters()
        setupBatchActions()
        setupRecyclerView()
        setupObservers()
    }

    private fun setupFilters() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.filterActiveChip -> AlertsFilter.ACTIVE
                R.id.filterTriggeredChip -> AlertsFilter.TRIGGERED
                R.id.filterPriceChip -> AlertsFilter.PRICE
                R.id.filterMetricsChip -> AlertsFilter.METRICS
                R.id.filterPairsChip -> AlertsFilter.PAIRS
                else -> AlertsFilter.ALL
            }
            renderFilteredList()
        }
    }

    private fun setupBatchActions() {
        binding.batchActivateButton.setOnClickListener {
            applyBatchUpdate { it.setActive(true) }
        }
        binding.batchPauseButton.setOnClickListener {
            applyBatchUpdate { it.setActive(false) }
        }
        binding.batchDeleteButton.setOnClickListener {
            applyBatchDelete()
        }
        binding.batchCancelButton.setOnClickListener {
            exitSelectionMode()
        }
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
                if (selectionMode) {
                    toggleSelection(watchItem)
                } else {
                    val symbol = watchItem.ticker ?: watchItem.ticker1 ?: return@GroupedWatchItemAdapter
                    (requireActivity() as? MainActivity)?.navigateToStockDetailFromAlerts(
                        symbol = symbol,
                        companyName = watchItem.companyName
                    )
                }
            },
            onItemLongClick = { watchItem ->
                if (!selectionMode) {
                    enterSelectionMode(watchItem)
                } else {
                    toggleSelection(watchItem)
                }
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
                if (selectionMode) return@SwipeToDeleteCallback false
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
                            latestItems = state.data
                            latestSortMode = sortMode
                            TriggerSeenTracker.markAllSeen(latestItems.map { it.item })
                            renderFilteredList()
                            // Update sort mode label
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
                            binding.emptyStateText.text = state.message
                            binding.sortModeLabel.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun renderFilteredList() {
        val filteredItems = latestItems.filter { uiState ->
            when (currentFilter) {
                AlertsFilter.ALL -> true
                AlertsFilter.ACTIVE -> uiState.item.isActive
                AlertsFilter.TRIGGERED -> uiState.item.isTriggered
                AlertsFilter.PRICE -> when (uiState.item.watchType) {
                    is WatchType.PriceTarget,
                    is WatchType.ATHBased,
                    is WatchType.DailyMove,
                    is WatchType.PriceRange -> true
                    else -> false
                }
                AlertsFilter.METRICS -> uiState.item.watchType is WatchType.KeyMetrics
                AlertsFilter.PAIRS -> uiState.item.watchType is WatchType.PricePair
            }
        }

        val visibleIds = filteredItems.map { it.item.id }.toSet()
        selectedRuleIds.retainAll(visibleIds)
        if (selectionMode && selectedRuleIds.isEmpty()) {
            selectionMode = false
        }

        groupedAdapter.submitGroupedList(filteredItems, latestSortMode)
        groupedAdapter.setSelectionMode(selectionMode)
        groupedAdapter.setSelectedItemIds(selectedRuleIds)
        updateBatchActionState()
        val showEmpty = filteredItems.isEmpty()
        binding.emptyStateContainer.visibility = if (showEmpty) View.VISIBLE else View.GONE
        if (showEmpty) {
            binding.emptyStateTitle.text = when (currentFilter) {
                AlertsFilter.ALL -> getString(R.string.alerts_empty_title)
                AlertsFilter.ACTIVE -> "Inga aktiva regler"
                AlertsFilter.TRIGGERED -> "Inga triggade regler"
                AlertsFilter.PRICE -> "Inga prisregler"
                AlertsFilter.METRICS -> "Inga nyckeltalsregler"
                AlertsFilter.PAIRS -> "Inga parregler"
            }
            binding.emptyStateText.text = when (currentFilter) {
                AlertsFilter.ALL -> getString(R.string.alerts_empty_subtitle)
                AlertsFilter.ACTIVE -> "Aktivera en regel eller skapa en ny för att se den här."
                AlertsFilter.TRIGGERED -> "Här visas regler som nyligen har utlöst."
                AlertsFilter.PRICE -> "Skapa prismål, drawdown eller dagsrörelse för att få en prislista här."
                AlertsFilter.METRICS -> "Skapa ett P/E-, P/S- eller yield-larm för att fylla den här vyn."
                AlertsFilter.PAIRS -> "Skapa ett aktiepar för att hantera parkonvergens här."
            }
        }
    }

    private fun enterSelectionMode(firstItem: WatchItem) {
        selectionMode = true
        selectedRuleIds.clear()
        selectedRuleIds.add(firstItem.id)
        groupedAdapter.setSelectionMode(true)
        groupedAdapter.setSelectedItemIds(selectedRuleIds)
        updateBatchActionState()
    }

    private fun toggleSelection(watchItem: WatchItem) {
        if (!selectionMode) return
        if (!selectedRuleIds.add(watchItem.id)) {
            selectedRuleIds.remove(watchItem.id)
        }
        if (selectedRuleIds.isEmpty()) {
            exitSelectionMode()
        } else {
            groupedAdapter.setSelectedItemIds(selectedRuleIds)
            updateBatchActionState()
        }
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedRuleIds.clear()
        groupedAdapter.setSelectionMode(false)
        updateBatchActionState()
    }

    private fun updateBatchActionState() {
        binding.batchActionsBar.visibility = if (selectionMode) View.VISIBLE else View.GONE
        if (selectionMode) {
            binding.batchSelectionCount.text = getString(R.string.batch_selected_count, selectedRuleIds.size)
        }
    }

    private fun selectedItems(): List<WatchItem> {
        return latestItems
            .filter { it.item.id in selectedRuleIds }
            .map { it.item }
    }

    private fun applyBatchUpdate(transform: (WatchItem) -> WatchItem) {
        val items = selectedItems()
        if (items.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                items.forEach { watchItem ->
                    viewModel.updateWatchItem(transform(watchItem))
                }
                exitSelectionMode()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Kunde inte uppdatera regler", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyBatchDelete() {
        val items = selectedItems()
        if (items.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ta bort regler")
            .setMessage("Ta bort ${items.size} valda regler?")
            .setPositiveButton("Ta bort") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        items.forEach { watchItem ->
                            viewModel.deleteWatchItem(watchItem)
                        }
                        exitSelectionMode()
                        Toast.makeText(requireContext(), "Regler borttagna", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte ta bort regler", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
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
