package com.example.stockflip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class StockWatchAdapter(
    private val onDelete: (StockWatchEntity) -> Unit
) : ListAdapter<StockWatchEntity, StockWatchAdapter.ViewHolder>(StockWatchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stock_watch, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val symbol: TextView = itemView.findViewById(R.id.symbol)
        private val dropInfo: TextView = itemView.findViewById(R.id.dropInfo)
        private val notifyInfo: TextView = itemView.findViewById(R.id.notifyInfo)

        fun bind(watch: StockWatchEntity) {
            symbol.text = watch.symbol
            
            val dropType = if (watch.isPercentage) "%" else "ABS"
            dropInfo.text = "Drop target: ${watch.dropValue} $dropType from ATH"
            
            notifyInfo.text = if (watch.notifyOnTrigger) "Notifications: ON" else "Notifications: OFF"
        }
    }
}

class StockWatchDiffCallback : DiffUtil.ItemCallback<StockWatchEntity>() {
    override fun areItemsTheSame(oldItem: StockWatchEntity, newItem: StockWatchEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: StockWatchEntity, newItem: StockWatchEntity): Boolean {
        return oldItem == newItem
    }
}
