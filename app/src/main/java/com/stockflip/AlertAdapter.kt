package com.stockflip

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stockflip.databinding.ItemAlertBinding
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Adapter för att visa alerts i StockDetailFragment.
 */
class AlertAdapter(
    private val onToggleActive: (WatchItem) -> Unit,
    private val onReactivate: (WatchItem) -> Unit,
    private val onDelete: (WatchItem) -> Unit
) : ListAdapter<WatchItem, AlertAdapter.AlertViewHolder>(AlertDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlertViewHolder(
        private val binding: ItemAlertBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))

        fun bind(watchItem: WatchItem) {
            binding.alertType.text = watchItem.getWatchTypeDisplayName()
            binding.alertCondition.text = formatAlertCondition(watchItem)
            binding.alertActiveSwitch.isChecked = watchItem.isActive
            binding.alertStatus.text = formatAlertStatus(watchItem)
            
            // Visa/dölj återaktivera-knapp baserat på status
            binding.reactivateButton.visibility = if (watchItem.isTriggered) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            
            // Event listeners
            binding.alertActiveSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != watchItem.isActive) {
                    onToggleActive(watchItem)
                }
            }
            
            binding.reactivateButton.setOnClickListener {
                onReactivate(watchItem)
            }
            
            binding.deleteButton.setOnClickListener {
                onDelete(watchItem)
            }
        }

        private fun formatAlertCondition(watchItem: WatchItem): String {
            return when (val watchType = watchItem.watchType) {
                is WatchType.PriceTarget -> {
                    val direction = when (watchType.direction) {
                        WatchType.PriceDirection.ABOVE -> "≥"
                        WatchType.PriceDirection.BELOW -> "≤"
                    }
                    "Pris $direction ${priceFormat.format(watchType.targetPrice)} SEK"
                }
                is WatchType.PriceRange -> {
                    "Pris mellan ${priceFormat.format(watchType.minPrice)} - ${priceFormat.format(watchType.maxPrice)} SEK"
                }
                is WatchType.ATHBased -> {
                    when (watchType.dropType) {
                        WatchType.DropType.PERCENTAGE -> {
                            "Drawdown ≥ ${priceFormat.format(watchType.dropValue)}% från 52w high"
                        }
                        WatchType.DropType.ABSOLUTE -> {
                            "Drawdown ≥ ${priceFormat.format(watchType.dropValue)} SEK från 52w high"
                        }
                    }
                }
                is WatchType.DailyMove -> {
                    val direction = when (watchType.direction) {
                        WatchType.DailyMoveDirection.UP -> "upp"
                        WatchType.DailyMoveDirection.DOWN -> "ned"
                        WatchType.DailyMoveDirection.BOTH -> "båda"
                    }
                    "Dagsrörelse ≥ ${priceFormat.format(watchType.percentThreshold)}% ($direction)"
                }
                else -> watchItem.getWatchTypeDisplayName()
            }
        }

        private fun formatAlertStatus(watchItem: WatchItem): String {
            return when {
                !watchItem.isActive -> "Status: Inaktiverad"
                watchItem.isTriggered -> "Status: Triggad (${watchItem.lastTriggeredDate ?: "idag"})"
                else -> "Status: Aktiv"
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

