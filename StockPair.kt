data class StockPair(
    val id: Int,
    val stockA: String,
    val stockNameA: String,
    val stockB: String,
    val stockNameB: String,
    val priceDifference: Double,
    val notifyWhenEqual: Boolean
)

