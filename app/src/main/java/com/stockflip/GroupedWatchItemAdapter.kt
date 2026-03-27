package com.stockflip

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.stockflip.CurrencyHelper
import com.stockflip.databinding.ItemSectionHeaderBinding
import com.stockflip.ui.ComposeWatchItemCard
import com.stockflip.ui.theme.GroupPosition
import com.stockflip.ui.theme.NP
import com.stockflip.ui.theme.StockFlipTheme
import kotlin.math.abs

/**
 * Sealed class to represent different types of items in the grouped list
 */
sealed class GroupedListItem {
    data class Header(val title: String) : GroupedListItem()
    data class WatchItemWrapper(
        val item: WatchItem,
        val live: LiveWatchData = LiveWatchData(),
        val groupPosition: GroupPosition = GroupPosition.ONLY,
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

/**
 * Adapter that groups watch items by type with section headers
 */
class GroupedWatchItemAdapter(
    private val onToggleActive: (WatchItem) -> Unit,
    private val onReactivate: (WatchItem) -> Unit,
    private val onDeleteClick: (WatchItem) -> Unit,
    private val onEditClick: (WatchItem) -> Unit,
    private val onItemClick: (WatchItem) -> Unit
) : ListAdapter<GroupedListItem, RecyclerView.ViewHolder>(GroupedListItemDiffCallback()) {

    private val highlightedItems = mutableSetOf<Int>()
    private val collapsedSections = mutableSetOf<String>()
    // Sorteringsläge
    private var sortMode: SortHelper.SortMode = SortHelper.SortMode.ADDITION_ORDER

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_WATCH_ITEM = 1
        private const val VIEW_TYPE_MULTIPLE_WATCHES = 2
        private const val VIEW_TYPE_SEPARATOR = 3
        private const val TAG = "GroupedWatchItemAdapter"
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GroupedListItem.Header -> VIEW_TYPE_HEADER
            is GroupedListItem.WatchItemWrapper -> VIEW_TYPE_WATCH_ITEM
            is GroupedListItem.MultipleWatchesWrapper -> VIEW_TYPE_MULTIPLE_WATCHES
            is GroupedListItem.GroupSeparator -> VIEW_TYPE_SEPARATOR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
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
            is GroupedListItem.Header -> {
                val headerHolder = holder as HeaderViewHolder
                headerHolder.bind(item.title)
            }
            is GroupedListItem.WatchItemWrapper -> {
                val watchItemHolder = holder as ComposeWatchItemViewHolder
                watchItemHolder.bind(item.item, item.live, item.groupPosition)
            }
            is GroupedListItem.MultipleWatchesWrapper -> {
                val multipleWatchesHolder = holder as MultipleWatchesViewHolder
                multipleWatchesHolder.bind(item) {
                    // Navigate to StockDetailFragment when clicked
                    // Use the first watch item to get symbol and company name
                    val firstItem = item.watchItems.firstOrNull()
                    if (firstItem != null) {
                        // Create a dummy WatchItem for navigation
                        val navItem = firstItem.copy(ticker = item.symbol, companyName = item.companyName)
                        onItemClick(navItem)
                    }
                }
            }
            is GroupedListItem.GroupSeparator -> {
                // No-op: separator view has no data to bind
            }
        }
    }
    
    /**
     * Rebuilds the list with only expanded sections and submits it
     */
    private fun rebuildAndSubmitList() {
        buildFilteredList(allWatchItems)
    }

    /**
     * Converts a list of WatchItems into grouped list by stock symbol.
     * Optionally updates sort mode atomically before building.
     */
    fun submitGroupedList(items: List<WatchItemUiState>, newSortMode: SortHelper.SortMode? = null) {
        Log.d(TAG, "=== submitGroupedList() called with ${items.size} items ===")
        if (newSortMode != null) sortMode = newSortMode
        allWatchItems = items
        buildFilteredList(items)
    }

    /**
     * Sätter sorteringsläge och uppdaterar listan.
     */
    fun setSortMode(mode: SortHelper.SortMode) {
        sortMode = mode
        rebuildAndSubmitList()
    }

    /**
     * Builds and submits a grouped list by stock symbol
     */
    private fun buildFilteredList(items: List<WatchItemUiState>) {
        val uiStateMap = items.associateBy { it.item.id }
        val groupedList = mutableListOf<GroupedListItem>()
        val activeItems = items.filter { it.item.isActive }

        // Separate PricePairs (they have two stocks, handle separately)
        val pricePairs = activeItems.filter { it.item.watchType is WatchType.PricePair }
        val sortedPricePairs = SortHelper.sortWatchItems(pricePairs.map { it.item }, sortMode)
            .mapNotNull { uiStateMap[it.id] }

        // Group single-stock items by ticker (inklusive Combined)
        val singleStockItems = activeItems.filter {
            it.item.watchType !is WatchType.PricePair && it.item.ticker != null
        }

        // Dela upp i aktier och krypto
        val stockOnlyItems = singleStockItems.filter { !StockSearchResult.isCryptoSymbol(it.item.ticker!!) }
        val cryptoItems = singleStockItems.filter { StockSearchResult.isCryptoSymbol(it.item.ticker!!) }

        val sortedStocksByTicker = SortHelper.sortTickerGroups(
            stockOnlyItems.groupBy { it.item.ticker!! }.mapValues { (_, v) -> v.map { it.item } }, sortMode
        ).mapValues { (_, wis) -> wis.mapNotNull { uiStateMap[it.id] } }

        val sortedCryptoByTicker = SortHelper.sortTickerGroups(
            cryptoItems.groupBy { it.item.ticker!! }.mapValues { (_, v) -> v.map { it.item } }, sortMode
        ).mapValues { (_, wis) -> wis.mapNotNull { uiStateMap[it.id] } }

        // Add Price Pairs section
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

        Log.d(TAG, "Built grouped list with ${groupedList.size} items")
        submitList(groupedList)
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
                    groupedList.add(GroupedListItem.WatchItemWrapper(uiState.item, uiState.live, GroupPosition.ONLY))
                }
                else -> watchItemsForTicker.forEachIndexed { index, uiState ->
                    val position = when (index) {
                        0                              -> GroupPosition.FIRST
                        watchItemsForTicker.lastIndex -> GroupPosition.LAST
                        else                           -> GroupPosition.MIDDLE
                    }
                    groupedList.add(GroupedListItem.WatchItemWrapper(uiState.item, uiState.live, position))
                }
            }
        }
    }

    /**
     * Stores all watch items for rebuilding the list when expansion state changes
     */
    private var allWatchItems: List<WatchItemUiState> = emptyList()

    inner class HeaderViewHolder(private val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
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

        fun bind(item: WatchItem, live: LiveWatchData, groupPosition: GroupPosition = GroupPosition.ONLY) {
            composeView.setContent {
                StockFlipTheme {
                    ComposeWatchItemCard(
                        item = item,
                        live = live,
                        groupPosition = groupPosition,
                        priceFormat = { value -> CurrencyHelper.formatDecimal(value) },
                        onItemClick = { onItemClick(item) },
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
                    com.stockflip.ui.components.cards.MultipleWatchesCard(
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

    // Old WatchItemViewHolder removed - now using ComposeWatchItemViewHolder

    class GroupedListItemDiffCallback : DiffUtil.ItemCallback<GroupedListItem>() {
        override fun areItemsTheSame(oldItem: GroupedListItem, newItem: GroupedListItem): Boolean {
            return when {
                oldItem is GroupedListItem.Header && newItem is GroupedListItem.Header ->
                    oldItem.title == newItem.title
                oldItem is GroupedListItem.WatchItemWrapper && newItem is GroupedListItem.WatchItemWrapper ->
                    oldItem.item.id == newItem.item.id
                oldItem is GroupedListItem.GroupSeparator && newItem is GroupedListItem.GroupSeparator ->
                    oldItem.id == newItem.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: GroupedListItem, newItem: GroupedListItem): Boolean {
            return when {
                oldItem is GroupedListItem.Header && newItem is GroupedListItem.Header ->
                    oldItem.title == newItem.title
                oldItem is GroupedListItem.WatchItemWrapper && newItem is GroupedListItem.WatchItemWrapper ->
                    oldItem == newItem
                oldItem is GroupedListItem.MultipleWatchesWrapper && newItem is GroupedListItem.MultipleWatchesWrapper -> {
                    oldItem.symbol == newItem.symbol &&
                    oldItem.companyName == newItem.companyName &&
                    oldItem.watchCount == newItem.watchCount &&
                    oldItem.triggeredCount == newItem.triggeredCount &&
                    oldItem.currentPrice == newItem.currentPrice &&
                    oldItem.dailyChangePercent == newItem.dailyChangePercent
                }
                oldItem is GroupedListItem.GroupSeparator && newItem is GroupedListItem.GroupSeparator ->
                    true
                else -> false
            }
        }
    }
}


