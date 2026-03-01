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
    data class WatchItemWrapper(val item: WatchItem) : GroupedListItem()
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
    private val onDeleteClick: (WatchItem) -> Unit,
    private val onEditClick: (WatchItem) -> Unit,
    private val onItemClick: (WatchItem) -> Unit
) : ListAdapter<GroupedListItem, RecyclerView.ViewHolder>(GroupedListItemDiffCallback()) {

    private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))
    private val highlightedItems = mutableSetOf<Int>()
    // Track which sections are expanded (default: all expanded)
    private val expandedSections = mutableSetOf<String>()
    // Sorteringsläge
    private var sortMode: SortHelper.SortMode = SortHelper.SortMode.ADDITION_ORDER
    // Anpassningsbar ordning för drag & drop (ticker -> position)
    private var customOrder: Map<String, Int> = emptyMap()

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
                val isExpanded = expandedSections.contains(item.title)
                val title = item.title
                headerHolder.bind(title, isExpanded) {
                    // Toggle expansion state
                    val wasExpanded = expandedSections.contains(title)
                    if (wasExpanded) {
                        expandedSections.remove(title)
                        Log.d(TAG, "Collapsing section '$title'")
                    } else {
                        expandedSections.add(title)
                        Log.d(TAG, "Expanding section '$title'")
                    }
                    Log.d(TAG, "After toggle - expandedSections: $expandedSections")
                    // Rebuild and submit the filtered list
                    rebuildAndSubmitList()
                }
            }
            is GroupedListItem.WatchItemWrapper -> {
                val watchItemHolder = holder as ComposeWatchItemViewHolder
                watchItemHolder.bind(item.item)
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
        Log.d(TAG, "Rebuilding list. Expanded sections: $expandedSections")
        Log.d(TAG, "All watch items count: ${allWatchItems.size}")
        buildFilteredList(allWatchItems)
    }

    /**
     * Converts a list of WatchItems into grouped list by stock symbol
     */
    fun submitGroupedList(items: List<WatchItem>) {
        Log.d(TAG, "=== submitGroupedList() called with ${items.size} items ===")
        // Store all items for rebuilding when expansion state changes
        allWatchItems = items
        
        // Initialize sections as expanded by default
        val pricePairs = items.filter { it.watchType is WatchType.PricePair }
        val singleStockItems = items.filter { 
            it.watchType !is WatchType.PricePair && it.ticker != null 
        }
        
        if (pricePairs.isNotEmpty() && !expandedSections.contains("Aktiepar")) {
            expandedSections.add("Aktiepar")
        }
        if (singleStockItems.isNotEmpty() && !expandedSections.contains("Aktier - Krypto")) {
            expandedSections.add("Aktier - Krypto")
        }

        // Build the filtered list grouped by stock
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
     * Sätter anpassningsbar ordning (för drag & drop).
     */
    fun setCustomOrder(order: Map<String, Int>) {
        customOrder = order
        if (sortMode == SortHelper.SortMode.CUSTOM) {
            rebuildAndSubmitList()
        }
    }

    /**
     * Builds and submits a filtered list grouped by stock symbol
     */
    private fun buildFilteredList(items: List<WatchItem>) {
        val groupedList = mutableListOf<GroupedListItem>()

        // Separate PricePairs (they have two stocks, handle separately)
        val pricePairs = items.filter { it.watchType is WatchType.PricePair }
        val sortedPricePairs = SortHelper.sortWatchItems(pricePairs, sortMode, customOrder)
        
        // Group single-stock items by ticker (inklusive Combined)
        val singleStockItems = items.filter { 
            it.watchType !is WatchType.PricePair && it.ticker != null 
        }
        
        // Group by ticker - kombinerade och vanliga bevakningar för samma ticker ska grupperas tillsammans
        val itemsByTicker = singleStockItems.groupBy { it.ticker!! }
        val sortedItemsByTicker = SortHelper.sortTickerGroups(itemsByTicker, sortMode, customOrder)

        // Add Price Pairs section (handle separately as they have two stocks)
        if (sortedPricePairs.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("Aktiepar"))
            val isExpanded = expandedSections.contains("Aktiepar")
            Log.d(TAG, "Aktiepar section: isExpanded=$isExpanded, items=${sortedPricePairs.size}")
            if (isExpanded) {
                sortedPricePairs.forEach { groupedList.add(GroupedListItem.WatchItemWrapper(it)) }
            }
        }

        // Add header for single stocks if there are any
        if (sortedItemsByTicker.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("Aktier - Krypto"))
            val isExpanded = expandedSections.contains("Aktier - Krypto")
            Log.d(TAG, "Aktier - Krypto section: isExpanded=$isExpanded, items=${sortedItemsByTicker.size} stocks")
            
            if (isExpanded) {
                // Process single-stock items grouped by ticker (sorted according to sortMode)
                sortedItemsByTicker.forEach { (ticker, watchItemsForTicker) ->
                    val companyName = watchItemsForTicker.firstOrNull()?.companyName
                    val currentPrice = watchItemsForTicker.firstOrNull()?.currentPrice ?: 0.0
                    
                    if (watchItemsForTicker.size == 1) {
                        // Single watch type - show the card directly
                        groupedList.add(GroupedListItem.WatchItemWrapper(watchItemsForTicker.first()))
                    } else {
                        // Multiple watch types - show "multiple watches" card
                        val dailyChangePercent = watchItemsForTicker.firstOrNull()?.currentDailyChangePercent
                        val triggeredCount = watchItemsForTicker.count { it.isTriggered }
                        groupedList.add(GroupedListItem.MultipleWatchesWrapper(
                            symbol = ticker,
                            companyName = companyName,
                            watchCount = watchItemsForTicker.size,
                            triggeredCount = triggeredCount,
                            currentPrice = currentPrice,
                            dailyChangePercent = dailyChangePercent,
                            watchItems = watchItemsForTicker
                        ))
                    }
                }
            }
        }

        Log.d(TAG, "Built filtered list with ${groupedList.size} items")
        submitList(groupedList)
    }
    
    /**
     * Stores all watch items for rebuilding the list when expansion state changes
     */
    private var allWatchItems: List<WatchItem> = emptyList()

    inner class HeaderViewHolder(private val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        private var currentTitle: String? = null
        private var currentIsExpanded: Boolean? = null // Use nullable to detect first bind
        private var isAnimating: Boolean = false // Track if animation is in progress
        
        fun bind(title: String, isExpanded: Boolean, onToggle: () -> Unit) {
            val titleChanged = currentTitle != title
            val stateChanged = currentIsExpanded != null && currentIsExpanded != isExpanded
            
            currentTitle = title
            binding.sectionHeaderText.text = title
            
            // Always ensure icon is correct based on expansion state
            // isExpanded = true (utfälld) → ic_expand_less (pekar uppåt ▲, kan fällas ihop)
            // isExpanded = false (infälld) → ic_expand_more (pekar nedåt ▼, kan fällas ut)
            // Always update icon to ensure it's correct
            updateIcon(isExpanded)
            
            currentIsExpanded = isExpanded
            isAnimating = false // Reset animation flag when bind is called
            
            // Remove any existing click listeners to avoid duplicates
            binding.root.setOnClickListener(null)
            // Set click listener - use currentIsExpanded to get the latest value
            binding.root.setOnClickListener {
                // Get current state from the ViewHolder's state, not from closure
                val currentState = currentIsExpanded ?: isExpanded
                Log.d(TAG, "Header clicked: $title, current isExpanded: $currentState")
                // Animate immediately on click (before state change)
                val newState = !currentState
                Log.d(TAG, "Animating icon change: $currentState -> $newState")
                isAnimating = true
                animateIconChange(currentState, newState)
                // Update local state immediately for smooth animation
                currentIsExpanded = newState
                onToggle()
            }
        }
        
        private fun updateIcon(isExpanded: Boolean) {
            val iconRes = if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            binding.expandIcon.setImageResource(iconRes)
            binding.expandIcon.rotation = 0f
            Log.d(TAG, "Icon updated: isExpanded=$isExpanded, icon=${if (isExpanded) "expand_less (up)" else "expand_more (down)"}")
        }
        
        private fun animateIconChange(fromExpanded: Boolean, toExpanded: Boolean) {
            // Cancel any ongoing animation
            binding.expandIcon.animate().cancel()
            
            // Ensure we start with the correct icon
            val startIconRes = if (fromExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            binding.expandIcon.setImageResource(startIconRes)
            binding.expandIcon.rotation = 0f
            Log.d(TAG, "Animation starting: fromExpanded=$fromExpanded (${if (fromExpanded) "up" else "down"}) -> toExpanded=$toExpanded (${if (toExpanded) "up" else "down"})")
            
            // Animate rotation: rotate 180 degrees to flip the icon
            binding.expandIcon.animate()
                .rotation(180f)
                .setDuration(200)
                .withEndAction {
                    // Update icon after animation completes to show correct direction
                    val endIconRes = if (toExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                    binding.expandIcon.setImageResource(endIconRes)
                    // Reset rotation for next animation
                    binding.expandIcon.rotation = 0f
                    isAnimating = false
                    Log.d(TAG, "Animation complete: icon set to ${if (toExpanded) "expand_less (up)" else "expand_more (down)"}")
                }
                .start()
        }
    }
    
    inner class ComposeWatchItemViewHolder(
        private val composeView: ComposeView
    ) : RecyclerView.ViewHolder(composeView) {
        
        fun bind(item: WatchItem) {
            composeView.setContent {
                StockFlipTheme {
                    ComposeWatchItemCard(
                        item = item,
                        priceFormat = { value -> priceFormat.format(value) },
                        onItemClick = {
                            onItemClick(item)
                        }
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
                            .padding(horizontal = 8.dp, vertical = 8.dp)
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


