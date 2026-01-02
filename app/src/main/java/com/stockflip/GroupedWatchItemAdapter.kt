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
import androidx.compose.ui.platform.ComposeView
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

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_WATCH_ITEM = 1
        private const val TAG = "GroupedWatchItemAdapter"
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GroupedListItem.Header -> VIEW_TYPE_HEADER
            is GroupedListItem.WatchItemWrapper -> VIEW_TYPE_WATCH_ITEM
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
     * Converts a list of WatchItems into grouped list with headers
     */
    fun submitGroupedList(items: List<WatchItem>) {
        Log.d(TAG, "=== submitGroupedList() called with ${items.size} items ===")
        // Store all items for rebuilding when expansion state changes
        allWatchItems = items
        
        // Group items by type
        val pricePairs = items.filter { it.watchType is WatchType.PricePair }
        val priceTargets = items.filter { it.watchType is WatchType.PriceTarget }
        val priceRanges = items.filter { it.watchType is WatchType.PriceRange }
        val keyMetrics = items.filter { it.watchType is WatchType.KeyMetrics }
        val athBased = items.filter { it.watchType is WatchType.ATHBased }
        val dailyMoves = items.filter { it.watchType is WatchType.DailyMove }
        
        Log.d(TAG, "KeyMetrics items in submitGroupedList: ${keyMetrics.size}")
        keyMetrics.forEach { item ->
            Log.d(TAG, "KeyMetrics in submitGroupedList: ${item.ticker}, currentMetricValue: ${item.currentMetricValue}")
        }

        // Initialize all sections as expanded by default (only for sections that have items)
        if (pricePairs.isNotEmpty() && !expandedSections.contains("Aktiepar")) {
            expandedSections.add("Aktiepar")
        }
        if (priceTargets.isNotEmpty() && !expandedSections.contains("Prismål")) {
            expandedSections.add("Prismål")
        }
        if (priceRanges.isNotEmpty() && !expandedSections.contains("Prisintervall")) {
            expandedSections.add("Prisintervall")
        }
        if (keyMetrics.isNotEmpty() && !expandedSections.contains("Nyckeltal")) {
            expandedSections.add("Nyckeltal")
        }
        if (athBased.isNotEmpty() && !expandedSections.contains("52-veckorshögsta")) {
            expandedSections.add("52-veckorshögsta")
        }
        if (dailyMoves.isNotEmpty() && !expandedSections.contains("Dagsrörelse")) {
            expandedSections.add("Dagsrörelse")
        }

        // Build the filtered list based on expansion state
        buildFilteredList(items)
    }
    
    /**
     * Builds and submits a filtered list based on current expansion state
     */
    private fun buildFilteredList(items: List<WatchItem>) {
        val groupedList = mutableListOf<GroupedListItem>()

        // Group items by type
        val pricePairs = items.filter { it.watchType is WatchType.PricePair }
        val priceTargets = items.filter { it.watchType is WatchType.PriceTarget }
        val priceRanges = items.filter { it.watchType is WatchType.PriceRange }
        val keyMetrics = items.filter { it.watchType is WatchType.KeyMetrics }
        val athBased = items.filter { it.watchType is WatchType.ATHBased }
        val dailyMoves = items.filter { it.watchType is WatchType.DailyMove }

        // Add Price Pairs section (only items if expanded)
        if (pricePairs.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("Aktiepar"))
            val isExpanded = expandedSections.contains("Aktiepar")
            Log.d(TAG, "Aktiepar section: isExpanded=$isExpanded, items=${pricePairs.size}")
            if (isExpanded) {
                pricePairs.forEach { groupedList.add(GroupedListItem.WatchItemWrapper(it)) }
            }
        }

        // Add Price Targets section (only items if expanded)
        if (priceTargets.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("Prismål"))
            val isExpanded = expandedSections.contains("Prismål")
            Log.d(TAG, "Prismål section: isExpanded=$isExpanded, items=${priceTargets.size}")
            if (isExpanded) {
                priceTargets.forEach { groupedList.add(GroupedListItem.WatchItemWrapper(it)) }
            }
        }

        // Add Price Ranges section (only items if expanded)
        if (priceRanges.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("Prisintervall"))
            val isExpanded = expandedSections.contains("Prisintervall")
            Log.d(TAG, "Prisintervall section: isExpanded=$isExpanded, items=${priceRanges.size}")
            if (isExpanded) {
                priceRanges.forEach { groupedList.add(GroupedListItem.WatchItemWrapper(it)) }
            }
        }

        // Add Key Metrics section (only items if expanded)
        if (keyMetrics.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("Nyckeltal"))
            val isExpanded = expandedSections.contains("Nyckeltal")
            Log.d(TAG, "Nyckeltal section: isExpanded=$isExpanded, items=${keyMetrics.size}")
            keyMetrics.forEach { item ->
                Log.d(TAG, "KeyMetrics item before adding to list: ${item.ticker}, currentMetricValue: ${item.currentMetricValue}")
            }
            if (isExpanded) {
                keyMetrics.forEach { groupedList.add(GroupedListItem.WatchItemWrapper(it)) }
            }
        }

        // Add ATH Based section (only items if expanded)
        if (athBased.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("52-veckorshögsta"))
            val isExpanded = expandedSections.contains("52-veckorshögsta")
            Log.d(TAG, "52-veckorshögsta section: isExpanded=$isExpanded, items=${athBased.size}")
            if (isExpanded) {
                athBased.forEach { groupedList.add(GroupedListItem.WatchItemWrapper(it)) }
            }
        }

        // Add Daily Moves section (only items if expanded)
        if (dailyMoves.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("Dagsrörelse"))
            val isExpanded = expandedSections.contains("Dagsrörelse")
            Log.d(TAG, "Dagsrörelse section: isExpanded=$isExpanded, items=${dailyMoves.size}")
            if (isExpanded) {
                dailyMoves.forEach { groupedList.add(GroupedListItem.WatchItemWrapper(it)) }
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
        
        fun bind(title: String, isExpanded: Boolean, onToggle: () -> Unit) {
            currentTitle = title
            binding.sectionHeaderText.text = title
            
            // Update icon based on expansion state
            binding.expandIcon.setImageResource(
                if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
            
            // Remove any existing click listeners to avoid duplicates
            binding.root.setOnClickListener(null)
            // Set click listener
            binding.root.setOnClickListener {
                Log.d(TAG, "Header clicked: $title, isExpanded: $isExpanded")
                onToggle()
            }
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
                    old.currentDropAbsolute == new.currentDropAbsolute
                }
                else -> false
            }
        }
    }
}


