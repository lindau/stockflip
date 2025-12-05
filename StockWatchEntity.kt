import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_watches")
data class StockWatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val stockName: String,
    val dropValue: Double,
    val isPercentage: Boolean,
    val notifyOnTrigger: Boolean,
    val ath: Double = 0.0 // Cache ATH to avoid fetching full history every time
)
