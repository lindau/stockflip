package com.stockflip

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stockflip.databinding.ItemStockPairBinding
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

class StockPairAdapter(
    private val onDeleteClick: (StockPair) -> Unit,
    private val onEditClick: (StockPair) -> Unit
) : ListAdapter<StockPair, StockPairAdapter.ViewHolder>(StockPairDiffCallback()) {

    private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))
    private val highlightedPairs = mutableSetOf<Int>() // Track which pairs are highlighted
    private val PRICE_EQUALITY_THRESHOLD = 0.01

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStockPairBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemStockPairBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnLongClickListener {
                onDeleteClick(getItem(adapterPosition))
                true
            }
            binding.notificationInfo.setOnClickListener {
                onEditClick(getItem(adapterPosition))
            }
        }

        fun bind(pair: StockPair) {
            Log.d(TAG, "Binding stock pair: ${pair.companyName1} - ${pair.companyName2}")
            
            // First stock
            binding.stockNames.text = "${pair.companyName1} (${pair.ticker1})"
            binding.priceInfo.text = "${priceFormat.format(pair.currentPrice1)} SEK"
            binding.priceIndicator1.setImageResource(
                if (pair.currentPrice1 > 0) R.drawable.ic_arrow_upward else R.drawable.ic_arrow_downward
            )
            binding.priceIndicator1.setColorFilter(
                binding.root.context.getColor(
                    if (pair.currentPrice1 > 0) R.color.price_up 
                    else R.color.price_down
                )
            )

            // Second stock
            binding.stockNames2.text = "${pair.companyName2} (${pair.ticker2})"
            binding.priceInfo2.text = "${priceFormat.format(pair.currentPrice2)} SEK"
            binding.priceIndicator2.setImageResource(
                if (pair.currentPrice2 > 0) R.drawable.ic_arrow_upward else R.drawable.ic_arrow_downward
            )
            binding.priceIndicator2.setColorFilter(
                binding.root.context.getColor(
                    if (pair.currentPrice2 > 0) R.color.price_up 
                    else R.color.price_down
                )
            )

            // Price difference and notification info
            val actualPriceDiff = abs(pair.currentPrice1 - pair.currentPrice2)
            binding.priceDifference.text = "Diff: ${priceFormat.format(actualPriceDiff)} SEK"
            
            // Set up notification chip with improved visualization
            binding.notificationInfo.apply {
                val notificationText = buildString {
                    if (pair.notifyWhenEqual) {
                        append("=")  // Enkelt likhetstecken
                    }
                    if (pair.priceDifference > 0) {
                        if (pair.notifyWhenEqual) append("  ")  // Extra mellanrum för separation
                        append("∆ ${priceFormat.format(pair.priceDifference)}")  // Delta-symbol för skillnad
                    }
                }
                
                text = when {
                    notificationText.isNotEmpty() -> notificationText
                    else -> "Inga notifieringar"
                }
                
                // Set chip colors
                setChipBackgroundColorResource(when {
                    pair.notifyWhenEqual || pair.priceDifference > 0 -> R.color.notification_active
                    else -> R.color.notification_inactive
                })
                
                isCheckable = false
                isClickable = true
                chipIcon = null
                textSize = 20f  // Större textstorlek för tydligare symboler
            }

            // Check if notification criteria are met and prices are not zero
            val shouldHighlight = pair.currentPrice1 != 0.0 && pair.currentPrice2 != 0.0 && (
                (pair.notifyWhenEqual && actualPriceDiff <= 0.01) ||
                (pair.priceDifference > 0 && actualPriceDiff >= pair.priceDifference)
            )

            // Check if highlight state changed
            val wasHighlighted = highlightedPairs.contains(pair.id)
            if (shouldHighlight && !wasHighlighted) {
                // Card just turned red, send notification
                showNotification(
                    binding.root.context,
                    "Price Alert",
                    buildNotificationMessage(pair, actualPriceDiff)
                )
                highlightedPairs.add(pair.id)
            } else if (!shouldHighlight && wasHighlighted) {
                // Card is no longer highlighted
                highlightedPairs.remove(pair.id)
            }

            // Set background color based on notification criteria
            binding.root.setCardBackgroundColor(binding.root.context.getColor(
                if (shouldHighlight) R.color.notification_highlight else android.R.color.white
            ))
        }

        private fun buildNotificationMessage(pair: StockPair, priceDiff: Double): String {
            return when {
                pair.notifyWhenEqual && priceDiff <= 0.01 ->
                    "${pair.companyName1} and ${pair.companyName2} prices are now equal at ${priceFormat.format(pair.currentPrice1)} SEK"
                priceDiff >= pair.priceDifference ->
                    "Price difference between ${pair.companyName1} and ${pair.companyName2} has reached ${priceFormat.format(priceDiff)} SEK"
                else -> ""
            }
        }

        private fun showNotification(context: Context, title: String, message: String) {
            // Create an Intent to open MainActivity
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            // Create PendingIntent
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
                .setContentIntent(pendingIntent)  // Add the PendingIntent
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "Sent notification: $title - $message")
        }
    }

    class StockPairDiffCallback : DiffUtil.ItemCallback<StockPair>() {
        override fun areItemsTheSame(oldItem: StockPair, newItem: StockPair): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StockPair, newItem: StockPair): Boolean {
            return oldItem == newItem &&
                   oldItem.currentPrice1 == newItem.currentPrice1 &&
                   oldItem.currentPrice2 == newItem.currentPrice2
        }
    }

    companion object {
        private const val TAG = "StockPairAdapter"
    }
} 