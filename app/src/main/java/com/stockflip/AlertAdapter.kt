package com.stockflip

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stockflip.ui.ComposeWatchItemCardWithControls
import com.stockflip.ui.theme.StockFlipTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Adapter för att visa alerts i StockDetailFragment.
 * Använder Compose-kort för att visa full information om bevakningar.
 */
class AlertAdapter(
    private val onToggleActive: (WatchItem) -> Unit,
    private val onReactivate: (WatchItem) -> Unit,
    private val onDelete: (WatchItem) -> Unit,
    private val onEdit: (WatchItem) -> Unit
) : ListAdapter<WatchItem, AlertAdapter.AlertViewHolder>(AlertDiffCallback()) {

    private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val composeView = ComposeView(parent.context)
        return AlertViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlertViewHolder(
        private val composeView: ComposeView
    ) : RecyclerView.ViewHolder(composeView) {

        fun bind(watchItem: WatchItem) {
            composeView.setContent {
                StockFlipTheme {
                    ComposeWatchItemCardWithControls(
                        item = watchItem,
                        priceFormat = { value -> priceFormat.format(value) },
                        onToggleActive = { onToggleActive(watchItem) },
                        onReactivate = { onReactivate(watchItem) },
                        onDelete = { onDelete(watchItem) },
                        onEdit = { onEdit(watchItem) }
                    )
                }
            }
        }
    }

    private class AlertDiffCallback : DiffUtil.ItemCallback<WatchItem>() {
        override fun areItemsTheSame(oldItem: WatchItem, newItem: WatchItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WatchItem, newItem: WatchItem): Boolean {
            return oldItem == newItem
        }
    }
}

