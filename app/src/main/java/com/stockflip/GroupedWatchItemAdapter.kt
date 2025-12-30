package com.stockflip

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stockflip.databinding.ItemSectionHeaderBinding
import com.stockflip.databinding.ItemWatchItemBinding
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
                val binding = ItemWatchItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                WatchItemViewHolder(binding)
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
                val watchItemHolder = holder as WatchItemViewHolder
                // Items are already filtered, so just bind normally
                watchItemHolder.bind(item.item, true)
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
        // Store all items for rebuilding when expansion state changes
        allWatchItems = items
        
        // Group items by type
        val pricePairs = items.filter { it.watchType is WatchType.PricePair }
        val priceTargets = items.filter { it.watchType is WatchType.PriceTarget }
        val keyMetrics = items.filter { it.watchType is WatchType.KeyMetrics }
        val athBased = items.filter { it.watchType is WatchType.ATHBased }

        // Initialize all sections as expanded by default (only for sections that have items)
        if (pricePairs.isNotEmpty() && !expandedSections.contains("Aktiepar")) {
            expandedSections.add("Aktiepar")
        }
        if (priceTargets.isNotEmpty() && !expandedSections.contains("Prismål")) {
            expandedSections.add("Prismål")
        }
        if (keyMetrics.isNotEmpty() && !expandedSections.contains("Nyckeltal")) {
            expandedSections.add("Nyckeltal")
        }
        if (athBased.isNotEmpty() && !expandedSections.contains("ATH-bevakning")) {
            expandedSections.add("ATH-bevakning")
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
        val keyMetrics = items.filter { it.watchType is WatchType.KeyMetrics }
        val athBased = items.filter { it.watchType is WatchType.ATHBased }

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

        // Add Key Metrics section (only items if expanded)
        if (keyMetrics.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("Nyckeltal"))
            val isExpanded = expandedSections.contains("Nyckeltal")
            Log.d(TAG, "Nyckeltal section: isExpanded=$isExpanded, items=${keyMetrics.size}")
            if (isExpanded) {
                keyMetrics.forEach { groupedList.add(GroupedListItem.WatchItemWrapper(it)) }
            }
        }

        // Add ATH Based section (only items if expanded)
        if (athBased.isNotEmpty()) {
            groupedList.add(GroupedListItem.Header("ATH-bevakning"))
            val isExpanded = expandedSections.contains("ATH-bevakning")
            Log.d(TAG, "ATH-bevakning section: isExpanded=$isExpanded, items=${athBased.size}")
            if (isExpanded) {
                athBased.forEach { groupedList.add(GroupedListItem.WatchItemWrapper(it)) }
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

    inner class WatchItemViewHolder(private val binding: ItemWatchItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val item = (getItem(adapterPosition) as GroupedListItem.WatchItemWrapper).item
                onItemClick(item)
            }
            binding.root.setOnLongClickListener {
                val item = (getItem(adapterPosition) as GroupedListItem.WatchItemWrapper).item
                onDeleteClick(item)
                true
            }
            binding.notificationInfo.setOnClickListener {
                val item = (getItem(adapterPosition) as GroupedListItem.WatchItemWrapper).item
                onEditClick(item)
            }
        }

        fun bind(item: WatchItem, isExpanded: Boolean) {
            Log.d(TAG, "Binding watch item: ${item.getDisplayName()}, type: ${item.watchType}")

            // Hide watch type chip since we now have section headers
            binding.watchTypeChip.visibility = View.GONE

            when (item.watchType) {
                is WatchType.PricePair -> bindPricePair(item)
                is WatchType.PriceTarget -> bindPriceTarget(item)
                is WatchType.KeyMetrics -> bindKeyMetrics(item)
                is WatchType.ATHBased -> bindATHBased(item)
            }
        }

        private fun bindPricePair(item: WatchItem) {
            binding.singleStockLayout.visibility = View.GONE
            binding.pairStockLayout1.visibility = View.VISIBLE
            binding.pairStockLayout2.visibility = View.VISIBLE
            binding.divider1.visibility = View.VISIBLE
            binding.divider2.visibility = View.VISIBLE

            binding.stockNames.text = "${item.companyName1 ?: item.ticker1} (${item.ticker1})"
            binding.priceInfo.text = item.formatPrice1()

            binding.stockNames2.text = "${item.companyName2 ?: item.ticker2} (${item.ticker2})"
            binding.priceInfo2.text = item.formatPrice2()

            val priceDiff = abs(item.currentPrice1 - item.currentPrice2)
            binding.priceDifference.text = "Diff: ${priceFormat.format(priceDiff)} SEK"

            val pricePair = item.watchType as WatchType.PricePair
            binding.notificationInfo.apply {
                val notificationText = buildString {
                    if (pricePair.notifyWhenEqual) {
                        append("=")
                    }
                    if (pricePair.priceDifference > 0) {
                        if (pricePair.notifyWhenEqual) append("  ")
                        append("∆ ${priceFormat.format(pricePair.priceDifference)}")
                    }
                }
                text = when {
                    notificationText.isNotEmpty() -> notificationText
                    else -> "Aktiepar"
                }
                setChipBackgroundColorResource(when {
                    pricePair.notifyWhenEqual || pricePair.priceDifference > 0 -> R.color.notification_active
                    else -> R.color.notification_inactive
                })
                isCheckable = false
                isClickable = true
                chipIcon = null
                textSize = 20f
            }

            val shouldHighlight = if (pricePair.notifyWhenEqual) {
                priceDiff <= 0.01
            } else {
                priceDiff >= pricePair.priceDifference
            }

            updateHighlightState(item.id, shouldHighlight, item, priceDiff)
        }

        private fun bindPriceTarget(item: WatchItem) {
            binding.singleStockLayout.visibility = View.VISIBLE
            binding.pairStockLayout1.visibility = View.GONE
            binding.pairStockLayout2.visibility = View.GONE
            binding.divider1.visibility = View.GONE
            binding.divider2.visibility = View.GONE

            binding.singleStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
            binding.singlePriceInfo.text = item.formatPrice()

            val priceTarget = item.watchType as WatchType.PriceTarget
            val directionText = when (priceTarget.direction) {
                WatchType.PriceDirection.ABOVE -> "Över"
                WatchType.PriceDirection.BELOW -> "Under"
            }
            binding.priceDifference.text = "Mål: $directionText ${priceFormat.format(priceTarget.targetPrice)} SEK"

            binding.notificationInfo.apply {
                text = "Prismål"
                setChipBackgroundColorResource(R.color.notification_active)
                isCheckable = false
                isClickable = true
                chipIcon = null
                textSize = 20f
            }

            val shouldHighlight = when (priceTarget.direction) {
                WatchType.PriceDirection.ABOVE -> item.currentPrice >= priceTarget.targetPrice
                WatchType.PriceDirection.BELOW -> item.currentPrice <= priceTarget.targetPrice
            }

            updateHighlightState(item.id, shouldHighlight, item, null)
        }

        private fun bindKeyMetrics(item: WatchItem) {
            binding.singleStockLayout.visibility = View.VISIBLE
            binding.pairStockLayout1.visibility = View.GONE
            binding.pairStockLayout2.visibility = View.GONE
            binding.divider1.visibility = View.GONE
            binding.divider2.visibility = View.GONE

            val keyMetrics = item.watchType as WatchType.KeyMetrics

            binding.singleStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
            
            val metricTypeName = when (keyMetrics.metricType) {
                WatchType.MetricType.PE_RATIO -> "P/E-tal"
                WatchType.MetricType.PS_RATIO -> "P/S-tal"
                WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
            }
            binding.singlePriceInfo.text = "$metricTypeName: ${item.formatMetricValue()}"

            val directionText = when (keyMetrics.direction) {
                WatchType.PriceDirection.ABOVE -> "Över"
                WatchType.PriceDirection.BELOW -> "Under"
            }
            val targetValueText = when (keyMetrics.metricType) {
                WatchType.MetricType.DIVIDEND_YIELD -> "${priceFormat.format(keyMetrics.targetValue)}%"
                else -> priceFormat.format(keyMetrics.targetValue)
            }
            binding.priceDifference.text = "Mål: $directionText $targetValueText"

            binding.notificationInfo.apply {
                text = metricTypeName
                setChipBackgroundColorResource(R.color.notification_active)
                isCheckable = false
                isClickable = true
                chipIcon = null
                textSize = 20f
            }

            val shouldHighlight = item.currentMetricValue != 0.0 && when (keyMetrics.direction) {
                WatchType.PriceDirection.ABOVE -> item.currentMetricValue >= keyMetrics.targetValue
                WatchType.PriceDirection.BELOW -> item.currentMetricValue <= keyMetrics.targetValue
            }

            updateHighlightState(item.id, shouldHighlight, item, null)
        }

        private fun bindATHBased(item: WatchItem) {
            binding.singleStockLayout.visibility = View.VISIBLE
            binding.pairStockLayout1.visibility = View.GONE
            binding.pairStockLayout2.visibility = View.GONE
            binding.divider1.visibility = View.GONE
            binding.divider2.visibility = View.GONE

            val athBased = item.watchType as WatchType.ATHBased

            binding.singleStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
            
            val dropTypeText = when (athBased.dropType) {
                WatchType.DropType.PERCENTAGE -> "Nedgång från ATH"
                WatchType.DropType.ABSOLUTE -> "Nedgång från ATH"
            }
            val currentDropText = item.formatATHDrop()
            binding.singlePriceInfo.text = "$dropTypeText: $currentDropText"
            
            if (item.currentATH > 0.0) {
                binding.singlePriceInfo.text = "ATH: ${priceFormat.format(item.currentATH)} SEK | $currentDropText"
            }

            val targetDropText = when (athBased.dropType) {
                WatchType.DropType.PERCENTAGE -> "${priceFormat.format(athBased.dropValue)}%"
                WatchType.DropType.ABSOLUTE -> "${priceFormat.format(athBased.dropValue)} SEK"
            }
            binding.priceDifference.text = "Mål: Nedgång $targetDropText"

            binding.notificationInfo.apply {
                text = "ATH-bevakning"
                setChipBackgroundColorResource(R.color.notification_active)
                isCheckable = false
                isClickable = true
                chipIcon = null
                textSize = 20f
            }

            val shouldHighlight = when (athBased.dropType) {
                WatchType.DropType.PERCENTAGE -> item.currentDropPercentage >= athBased.dropValue
                WatchType.DropType.ABSOLUTE -> item.currentDropAbsolute >= athBased.dropValue
            }

            updateHighlightState(item.id, shouldHighlight, item, null)
        }

        private fun updateHighlightState(
            itemId: Int,
            shouldHighlight: Boolean,
            item: WatchItem,
            priceDiff: Double?
        ) {
            val wasHighlighted = highlightedItems.contains(itemId)
            if (shouldHighlight && !wasHighlighted) {
                showNotification(
                    binding.root.context,
                    "Prisvarning",
                    buildNotificationMessage(item, priceDiff)
                )
                highlightedItems.add(itemId)
            } else if (!shouldHighlight && wasHighlighted) {
                highlightedItems.remove(itemId)
            }

            binding.root.setCardBackgroundColor(binding.root.context.getColor(
                if (shouldHighlight) R.color.notification_highlight else android.R.color.white
            ))
        }

        private fun buildNotificationMessage(item: WatchItem, priceDiff: Double?): String {
            return when (item.watchType) {
                is WatchType.PricePair -> {
                    when {
                        item.watchType.notifyWhenEqual && priceDiff != null && priceDiff <= 0.01 ->
                            "${item.companyName1} och ${item.companyName2} har nu samma pris på ${priceFormat.format(item.currentPrice1)} SEK"
                        priceDiff != null && priceDiff >= item.watchType.priceDifference ->
                            "Prisdifferensen mellan ${item.companyName1} och ${item.companyName2} har nått ${priceFormat.format(priceDiff)} SEK"
                        else -> ""
                    }
                }
                is WatchType.PriceTarget -> {
                    val priceTarget = item.watchType
                    val directionText = when (priceTarget.direction) {
                        WatchType.PriceDirection.ABOVE -> "överstigit"
                        WatchType.PriceDirection.BELOW -> "understigit"
                    }
                    "${item.companyName ?: item.ticker} har $directionText målpriset ${priceFormat.format(priceTarget.targetPrice)} SEK. Nuvarande pris: ${priceFormat.format(item.currentPrice)} SEK"
                }
                is WatchType.KeyMetrics -> {
                    val keyMetrics = item.watchType
                    val metricTypeName = when (keyMetrics.metricType) {
                        WatchType.MetricType.PE_RATIO -> "P/E-tal"
                        WatchType.MetricType.PS_RATIO -> "P/S-tal"
                        WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
                    }
                    val directionText = when (keyMetrics.direction) {
                        WatchType.PriceDirection.ABOVE -> "överstigit"
                        WatchType.PriceDirection.BELOW -> "understigit"
                    }
                    val targetValueText = when (keyMetrics.metricType) {
                        WatchType.MetricType.DIVIDEND_YIELD -> "${priceFormat.format(keyMetrics.targetValue)}%"
                        else -> priceFormat.format(keyMetrics.targetValue)
                    }
                    val currentValueText = when (keyMetrics.metricType) {
                        WatchType.MetricType.DIVIDEND_YIELD -> "${priceFormat.format(item.currentMetricValue)}%"
                        else -> priceFormat.format(item.currentMetricValue)
                    }
                    "${item.companyName ?: item.ticker} har $directionText målvärdet för $metricTypeName ($targetValueText). Nuvarande värde: $currentValueText"
                }
                is WatchType.ATHBased -> {
                    val athBased = item.watchType
                    val currentDropText = item.formatATHDrop()
                    val targetDropText = when (athBased.dropType) {
                        WatchType.DropType.PERCENTAGE -> "${priceFormat.format(athBased.dropValue)}%"
                        WatchType.DropType.ABSOLUTE -> "${priceFormat.format(athBased.dropValue)} SEK"
                    }
                    "${item.companyName ?: item.ticker} har nått nedgången $targetDropText från ATH. Nuvarande nedgång: $currentDropText"
                }
            }
        }

        private fun showNotification(context: Context, title: String, message: String) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, StockPriceUpdater.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notifications)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()
                notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing notification: ${e.message}", e)
            }
        }
    }

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
            return oldItem == newItem
        }
    }
}


