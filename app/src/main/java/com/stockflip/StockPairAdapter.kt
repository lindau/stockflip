package com.stockflip

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class StockPairAdapter(
    private val onDeleteClick: (StockPair) -> Unit,
    private val onEditClick: (StockPair) -> Unit
) : ListAdapter<StockPair, StockPairAdapter.ViewHolder>(StockPairDiffCallback()) {

    companion object {
        private const val TAG = "StockPairAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stock_pair, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stockNames: TextView = itemView.findViewById(R.id.stockNames)
        private val priceInfo: TextView = itemView.findViewById(R.id.priceInfo)
        private val notificationInfo: TextView = itemView.findViewById(R.id.notificationInfo)

        fun bind(pair: StockPair) {
            // Set stock names and prices
            stockNames.text = "${pair.companyName1} (${pair.ticker1}): ${getFormattedPrice(pair.currentPrice1)}"
            priceInfo.text = "${pair.companyName2} (${pair.ticker2}): ${getFormattedPrice(pair.currentPrice2)}"

            // Set notification info
            if (pair.priceDifference > 0 || pair.notifyWhenEqual) {
                notificationInfo.visibility = View.VISIBLE
                notificationInfo.text = buildString {
                    if (pair.priceDifference > 0) {
                        appendLine("Notify when price difference ≥ %.2f SEK".format(pair.priceDifference))
                    }
                    if (pair.notifyWhenEqual) {
                        appendLine("Will notify when prices are equal")
                    }
                    if (pair.currentPrice1 > 0.0 && pair.currentPrice2 > 0.0) {
                        append("Current difference: %.2f SEK".format(kotlin.math.abs(pair.currentPrice1 - pair.currentPrice2)))
                    }
                }
            } else {
                notificationInfo.visibility = View.GONE
            }

            // Set click listeners
            notificationInfo.setOnClickListener { onEditClick(pair) }
            itemView.setOnLongClickListener { 
                onDeleteClick(pair)
                true
            }
        }

        private fun getFormattedPrice(price: Double): String {
            return if (price > 0.0) {
                String.format("%.2f SEK", price)
            } else {
                "Loading..."
            }
        }
    }

    private class StockPairDiffCallback : DiffUtil.ItemCallback<StockPair>() {
        override fun areItemsTheSame(oldItem: StockPair, newItem: StockPair): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StockPair, newItem: StockPair): Boolean {
            return oldItem == newItem
        }
    }
} 