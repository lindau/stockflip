package com.stockflip

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.core.view.isVisible
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
                    YahooFinanceService,
                    database.stockNoteDao(),
                    database.podcastObservationDao()
                ) as T
            }
        }
    }

    private lateinit var groupedAdapter: GroupedWatchItemAdapter
    private var pendingDeleteSnackbar: Snackbar? = null
    private var currentFilter: AlertsFilter = AlertsFilter.ALL
        set(value) {
            field = value
            viewModel.selectedAlertsFilter = value
        }
    private var latestItems: List<WatchItemUiState> = emptyList()
    // Fel från en misslyckad laddning utan data — ligger kvar vid filterbyte tills en laddning lyckas.
    private var loadError: String? = null
    private val selectedRuleIds: MutableSet<Int> = mutableSetOf()
    private var selectionMode: Boolean = false
    private var reactivateAllTargets: List<WatchItem> = emptyList()

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
        setupReactivateAll()
        setupRecyclerView()
        // Återställ filtret från förra besöket i fliken (efter att listan satts upp, eftersom den ritas om).
        selectFilter(viewModel.selectedAlertsFilter)
        setupEmptyStateRetry()
        setupObservers()

        // Fragmentet skapas om vid varje flikbyte och lyssnar bara passivt på det delade flödet.
        // Trigga en tyst refresh så listan alltid fylls (och en fastnad Loading läks) utan att
        // användaren måste dra för att uppdatera.
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.refreshWatchItems(showLoading = false)
        }
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

    private fun selectFilter(filter: AlertsFilter) {
        val chipId = when (filter) {
            AlertsFilter.ALL -> R.id.filterAllChip
            AlertsFilter.ACTIVE -> R.id.filterActiveChip
            AlertsFilter.TRIGGERED -> R.id.filterTriggeredChip
            AlertsFilter.PRICE -> R.id.filterPriceChip
            AlertsFilter.METRICS -> R.id.filterMetricsChip
            AlertsFilter.PAIRS -> R.id.filterPairsChip
        }
        if (binding.filterChipGroup.checkedChipId == chipId) {
            currentFilter = filter
            renderFilteredList()
        } else {
            binding.filterChipGroup.check(chipId)
        }
    }

    private fun setupBatchActions() {
        binding.batchActivateButton.setOnClickListener {
            applyBatchActiveState(true)
        }
        binding.batchPauseButton.setOnClickListener {
            applyBatchActiveState(false)
        }
        binding.batchDeleteButton.setOnClickListener {
            applyBatchDelete()
        }
        binding.batchCancelButton.setOnClickListener {
            exitSelectionMode()
        }
    }

    private fun setupReactivateAll() {
        binding.reactivateAllButton.setOnClickListener {
            applyReactivateAll()
        }
    }

    private fun setupRecyclerView() {
        groupedAdapter = GroupedWatchItemAdapter(
            onToggleActive = { watchItem ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.toggleWatchItemActive(watchItem, !watchItem.isActive)
                    } catch (e: Exception) {
                        showMessage("Kunde inte uppdatera bevakning")
                    }
                }
            },
            onReactivate = { watchItem ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val result = viewModel.reactivateWatchItem(watchItem)
                        showMessage(result.toUserMessage())
                    } catch (e: Exception) {
                        showMessage("Kunde inte återaktivera bevakning")
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
                    if (watchItem.watchType is WatchType.PricePair) {
                        (requireActivity() as? MainActivity)?.navigateToPairDetailFromPairs(watchItem.id)
                        return@GroupedWatchItemAdapter
                    }
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
            },
            onAlertsSummaryClick = {
                selectFilter(AlertsFilter.TRIGGERED)
            }
        )

        binding.alertsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = groupedAdapter
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Bakgrundsuppdateringen indikeras av backgroundRefreshIndicator-linjen;
                    // här stänger vi bara av SwipeRefreshLayouts egen dra-spinner när kallet är klart.
                    viewModel.refreshWatchItems(showLoading = false)
                } catch (e: Exception) {
                    // Felet ytas via watchItemUiState-observern (Snackbar).
                } finally {
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
            // Högersvep (öppna detalj) fungerar alltid; vänstersvep bara när det finns en paus/aktivera-åtgärd.
            canSwipeLeft = { position ->
                val item = (groupedAdapter.currentList.getOrNull(position) as? GroupedListItem.WatchItemWrapper)?.item
                item != null && alertSwipeActionFor(item) != null
            },
            leftSwipeStyle = { position ->
                val item = (groupedAdapter.currentList.getOrNull(position) as? GroupedListItem.WatchItemWrapper)?.item
                when (item?.let { alertSwipeActionFor(it) }) {
                    AlertSwipeAction.ACTIVATE -> SwipeToDeleteCallback.LeftSwipeStyle.ACTIVATE
                    else -> SwipeToDeleteCallback.LeftSwipeStyle.PAUSE
                }
            },
            onSwiped = { position ->
                val listItem = groupedAdapter.currentList.getOrNull(position) as? GroupedListItem.WatchItemWrapper
                    ?: run {
                        groupedAdapter.notifyItemChanged(position)
                        return@SwipeToDeleteCallback
                    }
                // Återställ ItemTouchHelper-state direkt — DiffUtil animerar bort raden när Room uppdaterar
                groupedAdapter.notifyItemChanged(position)
                val watchItem = listItem.item
                // Svepet pausar en aktiv bevakning och aktiverar en pausad — det raderar aldrig.
                val action = alertSwipeActionFor(watchItem) ?: return@SwipeToDeleteCallback
                // Dismiss any pending snackbar from a previous swipe before showing the new one
                pendingDeleteSnackbar?.dismiss()
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // Vid fel visar MainActivity felet via actionError — ingen ångra-snackbar då.
                        if (!viewModel.toggleWatchItemActive(watchItem, action.activeAfterSwipe)) return@launch
                        val message = if (action == AlertSwipeAction.PAUSE) R.string.alert_deactivated else R.string.alert_activated
                        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                        snackbar.setAction(R.string.alert_undo) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    viewModel.toggleWatchItemActive(watchItem, !action.activeAfterSwipe)
                                } catch (e: Exception) {
                                    showMessage("Kunde inte ångra")
                                }
                            }
                        }
                        snackbar.show()
                        pendingDeleteSnackbar = snackbar
                    } catch (e: Exception) {
                        showMessage("Kunde inte uppdatera bevakningen")
                    }
                }
            },
            onSwipedRight = { position ->
                val listItem = groupedAdapter.currentList.getOrNull(position) as? GroupedListItem.WatchItemWrapper
                    ?: return@SwipeToDeleteCallback
                // Snap row back first, then navigate after animation completes
                groupedAdapter.notifyItemChanged(position)
                val item = listItem.item
                if (item.watchType is WatchType.PricePair) {
                    binding.alertsRecyclerView.postDelayed({
                        (requireActivity() as? MainActivity)?.navigateToPairDetailFromPairs(item.id)
                    }, 120)
                    return@SwipeToDeleteCallback
                }
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

    private fun setupEmptyStateRetry() {
        binding.emptyStateRetryButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                // showLoading = true: skelettet visas under försöket och ett nytt fel ytas som Error.
                viewModel.refreshWatchItems(showLoading = true)
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.watchItemUiState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            // Visa skelett bara vid äkta första laddning; har vi redan data
                            // låter vi listan ligga kvar och spinnern indikerar arbetet.
                            if (latestItems.isEmpty()) {
                                binding.emptyStateContainer.visibility = View.GONE
                                binding.skeletonLoadingView.visibility = View.VISIBLE
                            }
                        }
                        is UiState.Success -> {
                            binding.skeletonLoadingView.visibility = View.GONE
                            loadError = null
                            latestItems = state.data
                            renderFilteredList()
                        }
                        is UiState.Error -> {
                            binding.skeletonLoadingView.visibility = View.GONE
                            if (latestItems.isEmpty()) {
                                loadError = state.message
                                renderFilteredList()
                            } else {
                                Snackbar.make(binding.root, R.string.alerts_refresh_failed, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.watchItemsRefreshing.collect { refreshing ->
                    // Tunn linje istället för den runda spinnern — listan ligger kvar och är läsbar.
                    binding.backgroundRefreshIndicator.isVisible = refreshing
                }
            }
        }
    }

    private fun WatchItemUiState.isEligibleForBulkReactivation(): Boolean {
        return item.isManuallyReactivatable
    }

    private fun renderFilteredList() {
        val filteredItems = latestItems.filter { currentFilter.matches(it) }

        val visibleIds = filteredItems.map { it.item.id }.toSet()
        selectedRuleIds.retainAll(visibleIds)
        if (selectionMode && selectedRuleIds.isEmpty()) {
            selectionMode = false
        }

        groupedAdapter.submitAlertsList(filteredItems, latestItems)
        groupedAdapter.setSelectionMode(selectionMode)
        groupedAdapter.setSelectedItemIds(selectedRuleIds)
        updateHeaderState()
        updateBatchActionState()
        val eligibleForBulkReactivation = if (currentFilter == AlertsFilter.TRIGGERED) {
            filteredItems.filter { it.isEligibleForBulkReactivation() }
        } else {
            emptyList()
        }
        updateReactivateAllState(eligibleForBulkReactivation)
        renderEmptyState(watchListEmptyState(filteredItems.size, loadError))
    }

    private fun renderEmptyState(emptyState: WatchListEmptyState) {
        binding.emptyStateContainer.isVisible = emptyState !is WatchListEmptyState.Hidden
        binding.emptyStateRetryButton.isVisible = emptyState is WatchListEmptyState.LoadFailed
        when (emptyState) {
            WatchListEmptyState.Hidden -> Unit
            WatchListEmptyState.NoItems -> {
                binding.emptyStateTitle.setText(currentFilter.emptyTitleRes)
                binding.emptyStateText.setText(currentFilter.emptySubtitleRes)
            }
            is WatchListEmptyState.LoadFailed -> {
                binding.emptyStateTitle.setText(R.string.watch_items_load_failed_title)
                binding.emptyStateText.text = emptyState.message
            }
        }
    }

    private fun updateHeaderState() {
        val summary = alertsHeaderSummary(latestItems, WatchItem.getTodayDateString())
        binding.rulesSubtitle.text = getString(R.string.rules_subtitle_format, summary.triggeredToday, summary.active)
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

    private fun updateReactivateAllState(eligible: List<WatchItemUiState>) {
        reactivateAllTargets = eligible.map { it.item }
        binding.reactivateAllBar.visibility =
            if (currentFilter == AlertsFilter.TRIGGERED && eligible.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun applyReactivateAll() {
        val items = reactivateAllTargets
        if (items.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var guardedCount = 0
                items.forEach { watchItem ->
                    val result = viewModel.reactivateWatchItem(watchItem)
                    if (result.sameDayTriggerGuarded) guardedCount++
                }
                val message = if (guardedCount > 0) {
                    "${items.size} bevakningar återaktiverade. $guardedCount kan trigga först nästa handelsdag."
                } else {
                    "${items.size} bevakningar återaktiverade"
                }
                showMessage(message)
            } catch (e: Exception) {
                showMessage("Kunde inte återaktivera alla bevakningar")
            }
        }
    }

    private fun selectedItems(): List<WatchItem> {
        return latestItems
            .filter { it.item.id in selectedRuleIds }
            .map { it.item }
    }

    private fun applyBatchActiveState(isActive: Boolean) {
        val items = selectedItems()
        if (items.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Avbryt vid första fel och behåll markeringen så att användaren kan försöka igen.
                val allSucceeded = items.all { watchItem ->
                    viewModel.toggleWatchItemActive(watchItem, isActive)
                }
                if (allSucceeded) exitSelectionMode()
            } catch (e: Exception) {
                showMessage("Kunde inte uppdatera bevakningarna")
            }
        }
    }

    private fun applyBatchDelete() {
        val items = selectedItems()
        if (items.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ta bort bevakningar")
            .setMessage("Ta bort ${items.size} valda bevakningar?")
            .setPositiveButton("Ta bort") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // Avbryt vid första fel och behåll markeringen så att användaren kan försöka igen.
                        val allSucceeded = items.all { watchItem ->
                            viewModel.deleteWatchItem(watchItem)
                        }
                        if (allSucceeded) {
                            exitSelectionMode()
                            showMessage("Bevakningarna borttagna")
                        }
                    } catch (e: Exception) {
                        showMessage("Kunde inte ta bort bevakningarna")
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
                        if (viewModel.deleteWatchItem(watchItem)) {
                            showMessage(R.string.alert_deleted)
                        }
                    } catch (e: Exception) {
                        showMessage("Kunde inte ta bort bevakning")
                    }
                }
            }
            .setNegativeButton(R.string.alert_delete_negative, null)
            .show()
    }

    /** Återkoppling i listan visas som Snackbar, samma som svep-meddelandena. */
    private fun showMessage(message: String) {
        val root = _binding?.root ?: return
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showMessage(@StringRes messageRes: Int) = showMessage(getString(messageRes))

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
