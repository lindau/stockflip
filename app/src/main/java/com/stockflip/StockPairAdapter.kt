package com.stockflip

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stockflip.databinding.ItemStockPairBinding
import kotlin.math.abs

class StockPairAdapter(
    private val onDeleteClick: (StockPair) -> Unit,
    private val onEditClick: (StockPair) -> Unit
) : ListAdapter<StockPair, StockPairAdapter.ViewHolder>(StockPairDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStockPairBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pair = getItem(position)
        holder.bind(pair)
    }

    inner class ViewHolder(private val binding: ItemStockPairBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(pair: StockPair) {
            Log.d(TAG, "Binding stock pair: ${pair.companyName1} - ${pair.companyName2}")
            Log.d(TAG, "Current prices: ${pair.currentPrice1}, ${pair.currentPrice2}")

            // Set company names and prices on separate lines
            binding.stockNames.text = "${pair.companyName1} (${pair.ticker1}): ${pair.getFormattedPrice1()}"
            binding.priceInfo.text = "${pair.companyName2} (${pair.ticker2}): ${pair.getFormattedPrice2()}"

            // Calculate and show current price difference
            val currentDiff = if (pair.currentPrice1 > 0 && pair.currentPrice2 > 0) {
                abs(pair.currentPrice1 - pair.currentPrice2)
            } else null

            // Check if notification criteria are met
            val shouldHighlight = when {
                currentDiff == null -> false
                pair.notifyWhenEqual && abs(currentDiff) <= 0.01 -> true  // Prices are effectively equal
                pair.priceDifference > 0 && currentDiff >= pair.priceDifference -> true  // Difference threshold reached
                else -> false
            }

            // Update card background color
            binding.root.setCardBackgroundColor(
                if (shouldHighlight) {
                    android.graphics.Color.RED
                } else {
                    android.graphics.Color.WHITE
                }
            )

            binding.notificationInfo.text = buildString {
                if (currentDiff != null) {
                    appendLine("Current price difference: ${String.format("%.2f", currentDiff)} SEK")
                }
                if (pair.priceDifference > 0) {
                    append("Notify when price difference ≥ ${pair.getFormattedPriceDifference()} SEK")
                }
                if (pair.notifyWhenEqual) {
                    if (pair.priceDifference > 0) appendLine()
                    append("Will notify when prices are equal")
                }
            }

            // Set up click listeners
            binding.notificationInfo.setOnClickListener {
                onEditClick(pair)
            }
            itemView.setOnLongClickListener { 
                onDeleteClick(pair)
                true
            }
        }
    }

    companion object {
        private const val TAG = "StockPairAdapter"
    }

    private class StockPairDiffCallback : DiffUtil.ItemCallback<StockPair>() {
        override fun areItemsTheSame(oldItem: StockPair, newItem: StockPair): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StockPair, newItem: StockPair): Boolean {
            return oldItem == newItem &&
                   oldItem.currentPrice1 == newItem.currentPrice1 &&
                   oldItem.currentPrice2 == newItem.currentPrice2
        }
    }
} 