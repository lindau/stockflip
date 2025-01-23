// StockPairEntity.kt
@Entity(tableName = "stock_pairs")
data class StockPairEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val stockA: String,
    val stockB: String,
    val priceDifference: Double,
    val notifyWhenEqual: Boolean
)