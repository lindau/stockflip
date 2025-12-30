
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
            
            val criteriaText = when (val criteria = watch.watchCriteria) {
                null -> {
                    // Backward compatibility with old format
                    val dropType = if (watch.isPercentage) "%" else "ABS"
                    "Drop target: ${watch.dropValue} $dropType from ATH"
                }
                is WatchCriteria.PriceTargetCriteria -> {
                    val direction = if (criteria.comparison == ComparisonType.ABOVE) "över" else "under"
                    "Prisnivå: $direction ${criteria.threshold}"
                }
                is WatchCriteria.PERatioCriteria -> {
                    val direction = if (criteria.comparison == ComparisonType.ABOVE) "över" else "under"
                    "P/E-tal: $direction ${criteria.threshold}"
                }
                is WatchCriteria.PSRatioCriteria -> {
                    val direction = if (criteria.comparison == ComparisonType.ABOVE) "över" else "under"
                    "P/S-tal: $direction ${criteria.threshold}"
                }
                is WatchCriteria.ATHDropCriteria -> {
                    "Fall från ATH: ${criteria.dropPercentage}%"
                }
                is WatchCriteria.DailyHighDropCriteria -> {
                    "Fall från dagshögsta: ${criteria.dropPercentage}%"
                }
            }
            dropInfo.text = criteriaText
            
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
