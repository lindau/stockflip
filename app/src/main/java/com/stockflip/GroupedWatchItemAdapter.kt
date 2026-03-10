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
import com.stockflip.databinding.ItemSectionHeaderBinding
import com.stockflip.ui.ComposeWatchItemCard
import com.stockflip.ui.theme.GroupPosition
import com.stockflip.ui.theme.NP
import com.stockflip.ui.theme.StockFlipTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Sealed class to represent different types of items in the grouped list
 */
sealed class GroupedListItem {
    data class Header(val title: String) : GroupedListItem()
    data class WatchItemWrapper(
        val item: WatchItem,
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

    private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))
    private val highlightedItems = mutableSetOf<Int>()
    // Sorteringsläge
    private var sortMode: SortHelper.SortMode = SortHelper.SortMode.ADDITION_ORDER

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_WATCH_ITEM = 1
        private const val VIEW_TYPE_MULTIPLE_WATCHES = 2
        private const val TAG = "GroupedWatchItemAdapter"
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GroupedListItem.Header -> VIEW_TYPE_HEADER
            is GroupedListItem.WatchItemWrapper -> VIEW_TYPE_WATCH_ITEM
            is GroupedListItem.MultipleWatchesWrapper -> VIEW_TYPE_MULTIPLE_WATCHES
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
                watchItemHolder.bind(item.item, item.groupPosition)
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
    fun submitGroupedList(items: List<WatchItem>, newSortMode: SortHelper.SortMode? = null) {
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
    private fun buildFilteredList(items: List<WatchItem>) {
        val groupedList = mutableListOf<GroupedListItem>()

        // Separate PricePairs (they have two stocks, handle separately)
        val pricePairs = items.filter { it.watchType is WatchType.PricePair }
        val sortedPricePairs = SortHelper.sortWatchItems(pricePairs, sortMode)

        // Group single-stock items by ticker (inklusive Combined)
        val singleStockItems = items.filter {
            it.watchType !is WatchType.PricePair && it.ticker != null
        }

        // Group by ticker
        val itemsByTicker = singleStockItems.groupBy { it.ticker!! }
        val sortedItemsByTicker = SortHelper.sortTickerGroups(itemsByTicker, sortMode)

        // Add Price Pairs section
        if (sortedPricePairs.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("Aktiepar"))
            sortedPricePairs.forEach { groupedList.add(GroupedListItem.WatchItemWrapper(it)) }
        }

        // Add single-stock section — always expand all watches per ticker with GroupPosition
        if (sortedItemsByTicker.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("Aktier - Krypto"))
            sortedItemsByTicker.forEach { (_, watchItemsForTicker) ->
                when (watchItemsForTicker.size) {
                    1 -> groupedList.add(
                        GroupedListItem.WatchItemWrapper(watchItemsForTicker.first(), GroupPosition.ONLY)
                    )
                    else -> watchItemsForTicker.forEachIndexed { index, watchItem ->
                        val position = when (index) {
                            0                              -> GroupPosition.FIRST
                            watchItemsForTicker.lastIndex -> GroupPosition.LAST
                            else                           -> GroupPosition.MIDDLE
                        }
                        groupedList.add(GroupedListItem.WatchItemWrapper(watchItem, position))
                    }
                }
            }
        }

        Log.d(TAG, "Built grouped list with ${groupedList.size} items")
        submitList(groupedList)
    }
    
    /**
     * Stores all watch items for rebuilding the list when expansion state changes
     */
    private var allWatchItems: List<WatchItem> = emptyList()

    inner class HeaderViewHolder(private val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.sectionHeaderText.text = title
            binding.expandIcon.visibility = android.view.View.GONE
            binding.root.setOnClickListener(null)
        }
    }
    
    inner class ComposeWatchItemViewHolder(
        private val composeView: ComposeView
    ) : RecyclerView.ViewHolder(composeView) {

        fun bind(item: WatchItem, groupPosition: GroupPosition = GroupPosition.ONLY) {
            composeView.setContent {
                StockFlipTheme {
                    ComposeWatchItemCard(
                        item = item,
                        groupPosition = groupPosition,
                        priceFormat = { value -> priceFormat.format(value) },
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
                        priceFormat = { value -> priceFormat.format(value) },
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
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: GroupedListItem, newItem: GroupedListItem): Boolean {
            return when {
                oldItem is GroupedListItem.Header && newItem is GroupedListItem.Header ->
                    oldItem.title == newItem.title
                oldItem is GroupedListItem.WatchItemWrapper && newItem is GroupedListItem.WatchItemWrapper -> {
                    // Compare all fields, including those marked with @Ignore
                    val old = oldItem.item
                    val new = newItem.item

                    // Standard data class equals (compares DB fields)
                    old == new &&
                    oldItem.groupPosition == newItem.groupPosition &&
                    // Compare @Ignore fields manually
                    old.currentPrice1 == new.currentPrice1 &&
                    old.currentPrice2 == new.currentPrice2 &&
                    old.currentPrice == new.currentPrice &&
                    old.currentMetricValue == new.currentMetricValue &&
                    old.currentATH == new.currentATH &&
                    old.currentDropPercentage == new.currentDropPercentage &&
                    old.currentDropAbsolute == new.currentDropAbsolute &&
                    old.currentDailyChangePercent == new.currentDailyChangePercent
                }
                oldItem is GroupedListItem.MultipleWatchesWrapper && newItem is GroupedListItem.MultipleWatchesWrapper -> {
                    oldItem.symbol == newItem.symbol &&
                    oldItem.companyName == newItem.companyName &&
                    oldItem.watchCount == newItem.watchCount &&
                    oldItem.triggeredCount == newItem.triggeredCount &&
                    oldItem.currentPrice == newItem.currentPrice &&
                    oldItem.dailyChangePercent == newItem.dailyChangePercent
                }
                else -> false
            }
        }
    }
}


