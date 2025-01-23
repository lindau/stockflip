// Modified StockPairAdapter.kt
class StockPairAdapter(
    private var stockPairs: List<StockPairEntity>,
    private val onDelete: (StockPairEntity) -> Unit
) : RecyclerView.Adapter<StockPairAdapter.ViewHolder>() {

    private var currentList: List<StockPairEntity> = stockPairs

    fun submitList(newList: List<StockPairEntity>) {
        val oldList = currentList
        currentList = newList
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = 
                oldList[oldPos].id == newList[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = 
                oldList[oldPos] == newList[newPos]
        }).dispatchUpdatesTo(this)
    }

    fun getCurrentList() = currentList

    // ... rest of the adapter implementation remains similar ...
}