package com.stockflip

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.stockflip.databinding.ItemWatchItemBinding
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

class WatchItemAdapter(
    private val onDeleteClick: (WatchItem) -> Unit,
    private val onEditClick: (WatchItem) -> Unit,
    private val onItemClick: (WatchItem) -> Unit
) : ListAdapter<WatchItem, WatchItemAdapter.ViewHolder>(WatchItemDiffCallback()) {

    private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))
    private val highlightedItems = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWatchItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemWatchItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                onItemClick(getItem(adapterPosition))
            }
            binding.root.setOnLongClickListener {
                onDeleteClick(getItem(adapterPosition))
                true
            }
            binding.notificationInfo.setOnClickListener {
                onEditClick(getItem(adapterPosition))
            }
        }

        fun bind(item: WatchItem) {
            Log.d(TAG, "Binding watch item: ${item.getDisplayName()}, type: ${item.watchType}")

            // Set watch type chip
            binding.watchTypeChip.text = item.getWatchTypeDisplayName()

            when (item.watchType) {
                is WatchType.PricePair -> bindPricePair(item)
                is WatchType.PriceTarget -> bindPriceTarget(item)
                is WatchType.KeyMetrics -> bindKeyMetrics(item)
                is WatchType.ATHBased -> bindATHBased(item)
                is WatchType.PriceRange -> bindPriceRange(item)
                is WatchType.DailyMove -> bindDailyMove(item)
            }
        }

        private fun bindPricePair(item: WatchItem) {
            // Show both stocks (pair layout is always visible, single stock layout is hidden)
            binding.singleStockLayout.visibility = android.view.View.GONE
            binding.pairStockLayout1.visibility = android.view.View.VISIBLE
            binding.pairStockLayout2.visibility = android.view.View.VISIBLE
            binding.divider1.visibility = android.view.View.VISIBLE
            binding.divider2.visibility = android.view.View.VISIBLE

            // First stock
            binding.stockNames.text = "${item.companyName1 ?: item.ticker1} (${item.ticker1})"
            binding.priceInfo.text = item.formatPrice1()

            // Second stock
            binding.stockNames2.text = "${item.companyName2 ?: item.ticker2} (${item.ticker2})"
            binding.priceInfo2.text = item.formatPrice2()

            // Price difference and notification info
            val actualPriceDiff = abs(item.currentPrice1 - item.currentPrice2)
            binding.priceDifference.text = "Diff: ${priceFormat.format(actualPriceDiff)} SEK"

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
                    else -> "Inga notifieringar"
                }

                val secondaryContainerColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer)
                setChipBackgroundColor(ColorStateList.valueOf(secondaryContainerColor))

                isCheckable = false
                isClickable = true
                chipIcon = null
                textSize = 20f
            }

            // Check if notification criteria are met
            val shouldHighlight = item.currentPrice1 != 0.0 && item.currentPrice2 != 0.0 && (
                (pricePair.notifyWhenEqual && actualPriceDiff <= 0.01) ||
                (pricePair.priceDifference > 0 && actualPriceDiff >= pricePair.priceDifference)
            )

            updateHighlightState(item.id, shouldHighlight, item, actualPriceDiff)
        }

        private fun bindPriceTarget(item: WatchItem) {
            // Hide pair fields, show single stock layout
            binding.singleStockLayout.visibility = android.view.View.VISIBLE
            binding.pairStockLayout1.visibility = android.view.View.GONE
            binding.pairStockLayout2.visibility = android.view.View.GONE
            binding.divider1.visibility = android.view.View.GONE
            binding.divider2.visibility = android.view.View.GONE

            val priceTarget = item.watchType as WatchType.PriceTarget

            // Single stock info
            binding.singleStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
            binding.singlePriceInfo.text = item.formatPrice()

            // Target price info
            val directionText = when (priceTarget.direction) {
                WatchType.PriceDirection.ABOVE -> "Över"
                WatchType.PriceDirection.BELOW -> "Under"
            }
            binding.priceDifference.text = "$directionText ${priceFormat.format(priceTarget.targetPrice)} SEK"

            // Notification info
            binding.notificationInfo.apply {
                text = "$directionText ${priceFormat.format(priceTarget.targetPrice)}"
                val secondaryContainerColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer)
                setChipBackgroundColor(ColorStateList.valueOf(secondaryContainerColor))
                isCheckable = false
                isClickable = true
                chipIcon = null
                textSize = 20f
            }

            // Check if notification criteria are met
            val shouldHighlight = item.currentPrice != 0.0 && when (priceTarget.direction) {
                WatchType.PriceDirection.ABOVE -> item.currentPrice >= priceTarget.targetPrice
                WatchType.PriceDirection.BELOW -> item.currentPrice <= priceTarget.targetPrice
            }

            updateHighlightState(item.id, shouldHighlight, item, null)
        }

        private fun bindKeyMetrics(item: WatchItem) {
            // Hide pair fields, show single stock layout
            binding.singleStockLayout.visibility = android.view.View.VISIBLE
            binding.pairStockLayout1.visibility = android.view.View.GONE
            binding.pairStockLayout2.visibility = android.view.View.GONE
            binding.divider1.visibility = android.view.View.GONE
            binding.divider2.visibility = android.view.View.GONE

            val keyMetrics = item.watchType as WatchType.KeyMetrics

            // Single stock info
            binding.singleStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
            
            // Show metric value instead of price
            val metricTypeName = when (keyMetrics.metricType) {
                WatchType.MetricType.PE_RATIO -> "P/E-tal"
                WatchType.MetricType.PS_RATIO -> "P/S-tal"
                WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
            }
            binding.singlePriceInfo.text = "$metricTypeName: ${item.formatMetricValue()}"

            // Target value info
            val directionText = when (keyMetrics.direction) {
                WatchType.PriceDirection.ABOVE -> "Över"
                WatchType.PriceDirection.BELOW -> "Under"
            }
            val targetValueText = when (keyMetrics.metricType) {
                WatchType.MetricType.DIVIDEND_YIELD -> "${priceFormat.format(keyMetrics.targetValue)}%"
                else -> priceFormat.format(keyMetrics.targetValue)
            }
            binding.priceDifference.text = "$directionText $targetValueText"

            // Notification info - only show metric type, not target value (target is shown in priceDifference)
            binding.notificationInfo.apply {
                text = metricTypeName
                val secondaryContainerColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer)
                setChipBackgroundColor(ColorStateList.valueOf(secondaryContainerColor))
                isCheckable = false
                isClickable = true
                chipIcon = null
                textSize = 20f
            }

            // Check if notification criteria are met
            val shouldHighlight = item.currentMetricValue != 0.0 && when (keyMetrics.direction) {
                WatchType.PriceDirection.ABOVE -> item.currentMetricValue >= keyMetrics.targetValue
                WatchType.PriceDirection.BELOW -> item.currentMetricValue <= keyMetrics.targetValue
            }

            updateHighlightState(item.id, shouldHighlight, item, null)
        }

        private fun bindATHBased(item: WatchItem) {
            // Hide pair fields, show single stock layout
            binding.singleStockLayout.visibility = android.view.View.VISIBLE
            binding.pairStockLayout1.visibility = android.view.View.GONE
            binding.pairStockLayout2.visibility = android.view.View.GONE
            binding.divider1.visibility = android.view.View.GONE
            binding.divider2.visibility = android.view.View.GONE

            val athBased = item.watchType as WatchType.ATHBased

            // Single stock info
            binding.singleStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
            
            // Show ATH and drop info
            val dropTypeText = when (athBased.dropType) {
                WatchType.DropType.PERCENTAGE -> "Nedgång från ATH"
                WatchType.DropType.ABSOLUTE -> "Nedgång från ATH"
            }
            val currentDropText = item.formatATHDrop()
            binding.singlePriceInfo.text = "$dropTypeText: $currentDropText"
            
            if (item.currentATH > 0.0) {
                binding.singlePriceInfo.text = "ATH: ${priceFormat.format(item.currentATH)} SEK | $currentDropText"
            }

            // Target drop info
            val targetDropText = when (athBased.dropType) {
                WatchType.DropType.PERCENTAGE -> "${priceFormat.format(athBased.dropValue)}%"
                WatchType.DropType.ABSOLUTE -> "${priceFormat.format(athBased.dropValue)} SEK"
            }
            binding.priceDifference.text = "Nedgång $targetDropText"

            // Notification info - only show type, not target value (target is shown in priceDifference)
            binding.notificationInfo.apply {
                text = "ATH-bevakning"
                val secondaryContainerColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer)
                setChipBackgroundColor(ColorStateList.valueOf(secondaryContainerColor))
                isCheckable = false
                isClickable = true
                chipIcon = null
                textSize = 20f
            }

            // Check if notification criteria are met
            val shouldHighlight = when (athBased.dropType) {
                WatchType.DropType.PERCENTAGE -> item.currentDropPercentage >= athBased.dropValue
                WatchType.DropType.ABSOLUTE -> item.currentDropAbsolute >= athBased.dropValue
            }

            updateHighlightState(item.id, shouldHighlight, item, null)
        }

        private fun bindPriceRange(item: WatchItem) {
            binding.singleStockLayout.visibility = View.VISIBLE
            binding.pairStockLayout1.visibility = View.GONE
            binding.pairStockLayout2.visibility = View.GONE
            binding.divider1.visibility = View.GONE
            binding.divider2.visibility = View.GONE

            val priceRange = item.watchType as WatchType.PriceRange

            binding.singleStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
            binding.singlePriceInfo.text = item.formatPrice()

            binding.priceDifference.text = "Pris mellan ${priceFormat.format(priceRange.minPrice)} - ${priceFormat.format(priceRange.maxPrice)} SEK"

            binding.notificationInfo.apply {
                text = "Prisintervall"
                val secondaryContainerColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer)
                setChipBackgroundColor(ColorStateList.valueOf(secondaryContainerColor))
                isCheckable = false
                isClickable = true
                chipIcon = null
                textSize = 20f
            }

            val shouldHighlight = item.currentPrice >= priceRange.minPrice && item.currentPrice <= priceRange.maxPrice
            updateHighlightState(item.id, shouldHighlight, item, null)
        }

        private fun bindDailyMove(item: WatchItem) {
            binding.singleStockLayout.visibility = View.VISIBLE
            binding.pairStockLayout1.visibility = View.GONE
            binding.pairStockLayout2.visibility = View.GONE
            binding.divider1.visibility = View.GONE
            binding.divider2.visibility = View.GONE

            val dailyMove = item.watchType as WatchType.DailyMove

            binding.singleStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
            binding.singlePriceInfo.text = item.formatPrice()

            val directionText = when (dailyMove.direction) {
                WatchType.DailyMoveDirection.UP -> "upp"
                WatchType.DailyMoveDirection.DOWN -> "ned"
                WatchType.DailyMoveDirection.BOTH -> "båda"
            }
            binding.priceDifference.text = "Dagsrörelse ≥ ${priceFormat.format(dailyMove.percentThreshold)}% ($directionText)"

            binding.notificationInfo.apply {
                text = "Dagsrörelse"
                val secondaryContainerColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer)
                setChipBackgroundColor(ColorStateList.valueOf(secondaryContainerColor))
                isCheckable = false
                isClickable = true
                chipIcon = null
                textSize = 20f
            }

            // DailyMove kan inte highlightas baserat på currentPrice, behöver dailyChangePercent
            // Detta hanteras av StockPriceUpdater istället
            updateHighlightState(item.id, false, item, null)
        }

        private fun updateHighlightState(
            itemId: Int,
            shouldHighlight: Boolean,
            item: WatchItem,
            priceDiff: Double?
        ) {
            val wasHighlighted = highlightedItems.contains(itemId)
            if (shouldHighlight && !wasHighlighted) {
                // Item just triggered, send notification
                showNotification(
                    binding.root.context,
                    "Prisvarning",
                    buildNotificationMessage(item, priceDiff)
                )
                highlightedItems.add(itemId)
            } else if (!shouldHighlight && wasHighlighted) {
                // Item is no longer highlighted
                highlightedItems.remove(itemId)
            }

            // Set background color based on notification criteria
            // Use icon + text color instead of background color for triggered status
            binding.triggeredIcon.visibility = if (shouldHighlight) View.VISIBLE else View.GONE
            
            // Get error color from theme
            val errorColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorError)
            val onSurfaceColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
            
            binding.priceDifference.setTextColor(if (shouldHighlight) errorColor else onSurfaceColor)
            
            // Keep card background as surface (no pink background)
            val surfaceColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
            binding.root.setCardBackgroundColor(surfaceColor)
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
                    "${item.companyName ?: item.ticker} har gått ned $currentDropText från 52w high (${priceFormat.format(item.currentATH)} SEK). Mål: $targetDropText"
                }
                is WatchType.PriceRange -> {
                    val priceRange = item.watchType
                    "${item.companyName ?: item.ticker} har nått prisintervallet ${priceFormat.format(priceRange.minPrice)} - ${priceFormat.format(priceRange.maxPrice)} SEK. Nuvarande pris: ${priceFormat.format(item.currentPrice)} SEK"
                }
                is WatchType.DailyMove -> {
                    val dailyMove = item.watchType
                    val directionText = when (dailyMove.direction) {
                        WatchType.DailyMoveDirection.UP -> "upp"
                        WatchType.DailyMoveDirection.DOWN -> "ned"
                        WatchType.DailyMoveDirection.BOTH -> "båda"
                    }
                    "${item.companyName ?: item.ticker} har rört sig ${priceFormat.format(dailyMove.percentThreshold)}% $directionText idag"
                }
            }
        }

        private fun showNotification(context: Context, title: String, message: String) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, StockPriceUpdater.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_paid)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "Sent notification: $title - $message")
        }
    }

    class WatchItemDiffCallback : DiffUtil.ItemCallback<WatchItem>() {
        override fun areItemsTheSame(oldItem: WatchItem, newItem: WatchItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WatchItem, newItem: WatchItem): Boolean {
            return oldItem == newItem &&
                   oldItem.currentPrice1 == newItem.currentPrice1 &&
                   oldItem.currentPrice2 == newItem.currentPrice2 &&
                   oldItem.currentPrice == newItem.currentPrice &&
                   oldItem.currentMetricValue == newItem.currentMetricValue &&
                   oldItem.currentATH == newItem.currentATH &&
                   oldItem.currentDropPercentage == newItem.currentDropPercentage &&
                   oldItem.currentDropAbsolute == newItem.currentDropAbsolute
        }
    }

    companion object {
        private const val TAG = "WatchItemAdapter"
    }
}

