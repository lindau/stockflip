package com.stockflip

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stockflip.databinding.ItemSectionHeaderBinding
import com.stockflip.ui.ComposeWatchItemCard
import com.stockflip.ui.components.cards.MultipleWatchesCard
import com.stockflip.ui.components.cards.OverviewSummaryCard
import com.stockflip.ui.theme.GroupPosition
import com.stockflip.ui.theme.NP
import com.stockflip.ui.theme.StockFlipTheme
import kotlin.math.abs

sealed class GroupedListItem {
    data class OverviewSummary(
        val nearTriggerCount: Int,
        val triggeredTodayCount: Int,
        val activeCount: Int,
    ) : GroupedListItem()

    data class Header(val title: String) : GroupedListItem()

    data class WatchItemWrapper(
        val item: WatchItem,
        val live: LiveWatchData = LiveWatchData(),
        val groupPosition: GroupPosition = GroupPosition.ONLY,
        val nearTriggerLabel: String? = null,
    ) : GroupedListItem()

    data class MultipleWatchesWrapper(
        val symbol: String,
        val companyName: String?,
        val watchCount: Int,
        val triggeredCount: Int,
        val currentPrice: Double,
        val dailyChangePercent: Double?,
        val watchItems: List<WatchItem>
    ) : GroupedListItem()

    data class GroupSeparator(val id: Int) : GroupedListItem()
}

class GroupedWatchItemAdapter(
    private val onToggleActive: (WatchItem) -> Unit,
    private val onReactivate: (WatchItem) -> Unit,
    private val onDeleteClick: (WatchItem) -> Unit,
    private val onEditClick: (WatchItem) -> Unit,
    private val onItemClick: (WatchItem) -> Unit,
    private val onItemLongClick: ((WatchItem) -> Unit)? = null,
) : ListAdapter<GroupedListItem, RecyclerView.ViewHolder>(GroupedListItemDiffCallback()) {

    private val collapsedSections = mutableSetOf<String>()
    private var sortMode: SortHelper.SortMode = SortHelper.SortMode.ADDITION_ORDER
    private var displayMode: DisplayMode = DisplayMode.GROUPED
    private var allWatchItems: List<WatchItemUiState> = emptyList()
    private var selectionMode: Boolean = false
    private var selectedItemIds: Set<Int> = emptySet()

    private enum class DisplayMode {
        GROUPED,
        OVERVIEW,
    }

    private data class OverviewCandidate(
        val uiState: WatchItemUiState,
        val proximity: Double,
        val label: String,
    )

    companion object {
        private const val VIEW_TYPE_SUMMARY = 0
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_WATCH_ITEM = 2
        private const val VIEW_TYPE_MULTIPLE_WATCHES = 3
        private const val VIEW_TYPE_SEPARATOR = 4
        private const val TAG = "GroupedWatchItemAdapter"
        private const val VERY_CLOSE_THRESHOLD = 0.05
        private const val CLOSE_THRESHOLD = 0.12
        private const val NEAR_TRIGGER_SECTION_THRESHOLD = 0.20
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GroupedListItem.OverviewSummary -> VIEW_TYPE_SUMMARY
            is GroupedListItem.Header -> VIEW_TYPE_HEADER
            is GroupedListItem.WatchItemWrapper -> VIEW_TYPE_WATCH_ITEM
            is GroupedListItem.MultipleWatchesWrapper -> VIEW_TYPE_MULTIPLE_WATCHES
            is GroupedListItem.GroupSeparator -> VIEW_TYPE_SEPARATOR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SUMMARY -> {
                val composeView = ComposeView(parent.context)
                composeView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                OverviewSummaryViewHolder(composeView)
            }

            VIEW_TYPE_HEADER -> {
                val binding = ItemSectionHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                HeaderViewHolder(binding)
            }

            VIEW_TYPE_WATCH_ITEM -> {
                val composeView = ComposeView(parent.context)
                composeView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                ComposeWatchItemViewHolder(composeView)
            }

            VIEW_TYPE_MULTIPLE_WATCHES -> {
                val composeView = ComposeView(parent.context)
                composeView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                MultipleWatchesViewHolder(composeView)
            }

            VIEW_TYPE_SEPARATOR -> {
                val view = View(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (8 * parent.context.resources.displayMetrics.density).toInt()
                    )
                }
                object : RecyclerView.ViewHolder(view) {}
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GroupedListItem.OverviewSummary -> {
                (holder as OverviewSummaryViewHolder).bind(item)
            }

            is GroupedListItem.Header -> {
                (holder as HeaderViewHolder).bind(item.title)
            }

            is GroupedListItem.WatchItemWrapper -> {
                (holder as ComposeWatchItemViewHolder).bind(
                    item = item.item,
                    live = item.live,
                    groupPosition = item.groupPosition,
                    nearTriggerLabel = item.nearTriggerLabel,
                )
            }

            is GroupedListItem.MultipleWatchesWrapper -> {
                (holder as MultipleWatchesViewHolder).bind(item) {
                    val firstItem = item.watchItems.firstOrNull() ?: return@bind
                    val navItem = firstItem.copy(ticker = item.symbol, companyName = item.companyName)
                    onItemClick(navItem)
                }
            }

            is GroupedListItem.GroupSeparator -> Unit
        }
    }

    private fun rebuildAndSubmitList() {
        when (displayMode) {
            DisplayMode.GROUPED -> buildFilteredList(allWatchItems)
            DisplayMode.OVERVIEW -> buildOverviewList(allWatchItems)
        }
    }

    fun submitGroupedList(items: List<WatchItemUiState>, newSortMode: SortHelper.SortMode? = null) {
        if (newSortMode != null) sortMode = newSortMode
        displayMode = DisplayMode.GROUPED
        allWatchItems = items
        buildFilteredList(items)
    }

    fun submitOverviewList(items: List<WatchItemUiState>, newSortMode: SortHelper.SortMode? = null) {
        if (newSortMode != null) sortMode = newSortMode
        displayMode = DisplayMode.OVERVIEW
        allWatchItems = items
        buildOverviewList(items)
    }

    fun setSortMode(mode: SortHelper.SortMode) {
        sortMode = mode
        rebuildAndSubmitList()
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) {
            selectedItemIds = emptySet()
        }
        notifyItemRangeChanged(0, itemCount)
    }

    fun setSelectedItemIds(ids: Set<Int>) {
        selectedItemIds = ids
        notifyItemRangeChanged(0, itemCount)
    }

    private fun buildFilteredList(items: List<WatchItemUiState>) {
        val uiStateMap = items.associateBy { it.item.id }
        val groupedList = mutableListOf<GroupedListItem>()
        val activeItems = items.filter { it.item.isActive }

        val pricePairs = activeItems.filter { it.item.watchType is WatchType.PricePair }
        val sortedPricePairs = SortHelper.sortWatchItems(pricePairs.map { it.item }, sortMode)
            .mapNotNull { uiStateMap[it.id] }

        val singleStockItems = activeItems.filter {
            it.item.watchType !is WatchType.PricePair && it.item.ticker != null
        }

        val stockOnlyItems = singleStockItems.filter { item ->
            item.item.ticker?.let { ticker -> !StockSearchResult.isCryptoSymbol(ticker) } == true
        }
        val cryptoItems = singleStockItems.filter { item ->
            item.item.ticker?.let { ticker -> StockSearchResult.isCryptoSymbol(ticker) } == true
        }

        val sortedStocksByTicker = SortHelper.sortTickerGroups(
            stockOnlyItems.groupBy { requireNotNull(it.item.ticker) }.mapValues { (_, values) -> values.map { it.item } },
            sortMode
        ).mapValues { (_, watchItems) -> watchItems.mapNotNull { uiStateMap[it.id] } }

        val sortedCryptoByTicker = SortHelper.sortTickerGroups(
            cryptoItems.groupBy { requireNotNull(it.item.ticker) }.mapValues { (_, values) -> values.map { it.item } },
            sortMode
        ).mapValues { (_, watchItems) -> watchItems.mapNotNull { uiStateMap[it.id] } }

        if (sortedPricePairs.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("Aktiepar"))
            if ("Aktiepar" !in collapsedSections) {
                sortedPricePairs.forEach { uiState ->
                    groupedList.add(GroupedListItem.WatchItemWrapper(uiState.item, uiState.live))
                }
            }
        }

        addTickerGroupSection(groupedList, "Aktier", sortedStocksByTicker)
        addTickerGroupSection(groupedList, "Krypto", sortedCryptoByTicker)

        submitList(groupedList)
    }

    private fun buildOverviewList(items: List<WatchItemUiState>) {
        val groupedList = mutableListOf<GroupedListItem>()
        val stockItems = items.filter { it.item.watchType !is WatchType.PricePair }
        val today = WatchItem.getTodayDateString()

        val triggeredItems = stockItems
            .filter { it.item.isTriggered || TriggerSeenTracker.isNew(it.item) }
            .sortedByDescending { it.item.lastTriggeredDate ?: "" }

        val nearTriggerItems = stockItems
            .filter { it.item.isActive && !it.item.isTriggered }
            .mapNotNull { uiState ->
                val proximity = calculateTriggerProximity(uiState) ?: return@mapNotNull null
                if (proximity <= NEAR_TRIGGER_SECTION_THRESHOLD) {
                    OverviewCandidate(
                        uiState = uiState,
                        proximity = proximity,
                        label = if (proximity <= VERY_CLOSE_THRESHOLD) "Mycket nära" else "Nära trigger"
                    )
                } else {
                    null
                }
            }
            .sortedBy { it.proximity }
            .map { it.uiState.item.id to it }

        val nearTriggerIds = nearTriggerItems.map { it.first }.toSet()
        val triggeredIds = triggeredItems.map { it.item.id }.toSet()

        val activeItems = sortForOverview(
            stockItems.filter { it.item.isActive && it.item.id !in triggeredIds && it.item.id !in nearTriggerIds }
        )
        val inactiveItems = sortForOverview(stockItems.filter { !it.item.isActive })

        groupedList.add(
            GroupedListItem.OverviewSummary(
                nearTriggerCount = nearTriggerItems.size,
                triggeredTodayCount = triggeredItems.count { it.item.lastTriggeredDate == today },
                activeCount = stockItems.count { it.item.isActive }
            )
        )

        addOverviewSection(groupedList, "Nytt och triggade", triggeredItems)
        addOverviewSection(
            groupedList = groupedList,
            header = "Nära att triggas",
            items = nearTriggerItems.map { it.second.uiState },
            nearLabels = nearTriggerItems.associate { it.first to it.second.label }
        )
        addOverviewSection(groupedList, "Aktiva case", activeItems)
        addOverviewSection(groupedList, "Inaktiva", inactiveItems)

        Log.d(TAG, "Built overview list with ${groupedList.size} items")
        submitList(groupedList)
    }

    private fun addOverviewSection(
        groupedList: MutableList<GroupedListItem>,
        header: String,
        items: List<WatchItemUiState>,
        nearLabels: Map<Int, String> = emptyMap()
    ) {
        if (items.isEmpty()) return
        if (groupedList.isNotEmpty()) {
            groupedList.add(GroupedListItem.GroupSeparator(groupedList.size))
        }
        groupedList.add(GroupedListItem.Header(header))
        if (header in collapsedSections) return

        items.forEachIndexed { index, uiState ->
            val groupPosition = when (index) {
                0 -> if (items.size == 1) GroupPosition.ONLY else GroupPosition.FIRST
                items.lastIndex -> GroupPosition.LAST
                else -> GroupPosition.MIDDLE
            }
            groupedList.add(
                GroupedListItem.WatchItemWrapper(
                    item = uiState.item,
                    live = uiState.live,
                    groupPosition = groupPosition,
                    nearTriggerLabel = nearLabels[uiState.item.id]
                )
            )
        }
    }

    private fun sortForOverview(items: List<WatchItemUiState>): List<WatchItemUiState> {
        return when (sortMode) {
            SortHelper.SortMode.ALPHABETICAL -> items.sortedBy {
                (it.item.companyName ?: it.item.ticker ?: "").lowercase()
            }

            SortHelper.SortMode.ADDITION_ORDER -> items.sortedByDescending { it.item.id }
        }
    }

    private fun calculateTriggerProximity(uiState: WatchItemUiState): Double? {
        val item = uiState.item
        val live = uiState.live
        return when (val watchType = item.watchType) {
            is WatchType.PriceTarget -> {
                if (live.currentPrice <= 0.0 || watchType.targetPrice <= 0.0) null
                else when (watchType.direction) {
                    WatchType.PriceDirection.ABOVE -> {
                        val remaining = watchType.targetPrice - live.currentPrice
                        if (remaining <= 0.0) 0.0 else remaining / watchType.targetPrice
                    }
                    WatchType.PriceDirection.BELOW -> {
                        val remaining = live.currentPrice - watchType.targetPrice
                        if (remaining <= 0.0) 0.0 else remaining / watchType.targetPrice
                    }
                }
            }

            is WatchType.KeyMetrics -> {
                if (live.currentMetricValue <= 0.0 || watchType.targetValue <= 0.0) null
                else when (watchType.direction) {
                    WatchType.PriceDirection.ABOVE -> {
                        val remaining = watchType.targetValue - live.currentMetricValue
                        if (remaining <= 0.0) 0.0 else remaining / watchType.targetValue
                    }
                    WatchType.PriceDirection.BELOW -> {
                        val remaining = live.currentMetricValue - watchType.targetValue
                        if (remaining <= 0.0) 0.0 else remaining / watchType.targetValue
                    }
                }
            }

            is WatchType.ATHBased -> {
                val currentValue = when (watchType.dropType) {
                    WatchType.DropType.PERCENTAGE -> live.currentDropPercentage
                    WatchType.DropType.ABSOLUTE -> live.currentDropAbsolute
                }
                if (currentValue <= 0.0 || watchType.dropValue <= 0.0) null
                else {
                    val remaining = watchType.dropValue - currentValue
                    if (remaining <= 0.0) 0.0 else remaining / watchType.dropValue
                }
            }

            is WatchType.DailyMove -> {
                val currentChange = live.currentDailyChangePercent ?: return null
                val currentMove = when (watchType.direction) {
                    WatchType.DailyMoveDirection.UP -> currentChange.coerceAtLeast(0.0)
                    WatchType.DailyMoveDirection.DOWN -> (-currentChange).coerceAtLeast(0.0)
                    WatchType.DailyMoveDirection.BOTH -> abs(currentChange)
                }
                if (watchType.percentThreshold <= 0.0) null
                else {
                    val remaining = watchType.percentThreshold - currentMove
                    if (remaining <= 0.0) 0.0 else remaining / watchType.percentThreshold
                }
            }

            is WatchType.PriceRange -> {
                if (live.currentPrice <= 0.0) return null
                when {
                    live.currentPrice in watchType.minPrice..watchType.maxPrice -> 0.0
                    live.currentPrice < watchType.minPrice -> abs(live.currentPrice - watchType.minPrice) / watchType.minPrice
                    else -> abs(live.currentPrice - watchType.maxPrice) / watchType.maxPrice
                }
            }

            is WatchType.PricePair -> null
            is WatchType.Combined -> null
        }
    }

    private fun addTickerGroupSection(
        groupedList: MutableList<GroupedListItem>,
        header: String,
        sortedItemsByTicker: Map<String, List<WatchItemUiState>>
    ) {
        if (sortedItemsByTicker.isEmpty()) return
        if (groupedList.isNotEmpty()) {
            groupedList.add(GroupedListItem.GroupSeparator(groupedList.size))
        }
        groupedList.add(GroupedListItem.Header(header))
        if (header in collapsedSections) return

        sortedItemsByTicker.entries.forEachIndexed { groupIndex, (_, watchItemsForTicker) ->
            if (groupIndex > 0) {
                groupedList.add(GroupedListItem.GroupSeparator(groupedList.size))
            }
            when (watchItemsForTicker.size) {
                1 -> {
                    val uiState = watchItemsForTicker.first()
                    groupedList.add(
                        GroupedListItem.WatchItemWrapper(uiState.item, uiState.live, GroupPosition.ONLY)
                    )
                }

                else -> watchItemsForTicker.forEachIndexed { index, uiState ->
                    val position = when (index) {
                        0 -> GroupPosition.FIRST
                        watchItemsForTicker.lastIndex -> GroupPosition.LAST
                        else -> GroupPosition.MIDDLE
                    }
                    groupedList.add(GroupedListItem.WatchItemWrapper(uiState.item, uiState.live, position))
                }
            }
        }
    }

    inner class OverviewSummaryViewHolder(
        private val composeView: ComposeView
    ) : RecyclerView.ViewHolder(composeView) {
        fun bind(summary: GroupedListItem.OverviewSummary) {
            composeView.setContent {
                StockFlipTheme {
                    OverviewSummaryCard(
                        nearTriggerCount = summary.nearTriggerCount,
                        triggeredTodayCount = summary.triggeredTodayCount,
                        activeCount = summary.activeCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NP.cardOuterH, vertical = NP.cardOuterV)
                    )
                }
            }
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.sectionHeaderText.text = title
            val isCollapsed = title in collapsedSections
            binding.expandIcon.visibility = View.VISIBLE
            binding.expandIcon.setImageResource(
                if (isCollapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less
            )
            binding.root.setOnClickListener {
                if (title in collapsedSections) collapsedSections.remove(title)
                else collapsedSections.add(title)
                rebuildAndSubmitList()
            }
        }
    }

    inner class ComposeWatchItemViewHolder(
        private val composeView: ComposeView
    ) : RecyclerView.ViewHolder(composeView) {
        fun bind(item: WatchItem, live: LiveWatchData, groupPosition: GroupPosition, nearTriggerLabel: String?) {
            composeView.setContent {
                StockFlipTheme {
                    val isSelected = selectedItemIds.contains(item.id)
                    val effectiveContainerColor = if (isSelected) {
                        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.surface
                    }
                    ComposeWatchItemCard(
                        item = item,
                        live = live,
                        groupPosition = groupPosition,
                        priceFormat = { value -> CurrencyHelper.formatDecimal(value) },
                        onItemClick = {
                            if (selectionMode) {
                                onItemLongClick?.invoke(item)
                            } else {
                                onItemClick(item)
                            }
                        },
                        isNew = TriggerSeenTracker.isNew(item),
                        nearTriggerLabel = nearTriggerLabel,
                        onLongClick = if (onItemLongClick != null) ({ onItemLongClick.invoke(item) }) else null,
                        containerColor = effectiveContainerColor,
                    )
                }
            }
        }
    }

    inner class MultipleWatchesViewHolder(
        private val composeView: ComposeView
    ) : RecyclerView.ViewHolder(composeView) {
        fun bind(wrapper: GroupedListItem.MultipleWatchesWrapper, onClick: () -> Unit) {
            composeView.setContent {
                StockFlipTheme {
                    MultipleWatchesCard(
                        symbol = wrapper.symbol,
                        companyName = wrapper.companyName,
                        watchCount = wrapper.watchCount,
                        triggeredCount = wrapper.triggeredCount,
                        currentPrice = wrapper.currentPrice,
                        dailyChangePercent = wrapper.dailyChangePercent,
                        priceFormat = { value -> CurrencyHelper.formatDecimal(value) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = NP.cardOuterH, vertical = NP.cardOuterV)
                    )
                }
            }
        }
    }

    class GroupedListItemDiffCallback : DiffUtil.ItemCallback<GroupedListItem>() {
        override fun areItemsTheSame(oldItem: GroupedListItem, newItem: GroupedListItem): Boolean {
            return when {
                oldItem is GroupedListItem.OverviewSummary && newItem is GroupedListItem.OverviewSummary -> true
                oldItem is GroupedListItem.Header && newItem is GroupedListItem.Header ->
                    oldItem.title == newItem.title
                oldItem is GroupedListItem.WatchItemWrapper && newItem is GroupedListItem.WatchItemWrapper ->
                    oldItem.item.id == newItem.item.id
                oldItem is GroupedListItem.MultipleWatchesWrapper && newItem is GroupedListItem.MultipleWatchesWrapper ->
                    oldItem.symbol == newItem.symbol
                oldItem is GroupedListItem.GroupSeparator && newItem is GroupedListItem.GroupSeparator ->
                    oldItem.id == newItem.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: GroupedListItem, newItem: GroupedListItem): Boolean {
            return when {
                oldItem is GroupedListItem.OverviewSummary && newItem is GroupedListItem.OverviewSummary ->
                    oldItem == newItem
                oldItem is GroupedListItem.Header && newItem is GroupedListItem.Header ->
                    oldItem.title == newItem.title
                oldItem is GroupedListItem.WatchItemWrapper && newItem is GroupedListItem.WatchItemWrapper ->
                    oldItem == newItem
                oldItem is GroupedListItem.MultipleWatchesWrapper && newItem is GroupedListItem.MultipleWatchesWrapper ->
                    oldItem == newItem
                oldItem is GroupedListItem.GroupSeparator && newItem is GroupedListItem.GroupSeparator -> true
                else -> false
            }
        }
    }
}
