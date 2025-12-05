import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StockWatchDao {
    @Query("SELECT * FROM stock_watches")
    fun getAllStockWatches(): Flow<List<StockWatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockWatch(stockWatch: StockWatchEntity)

    @Delete
    suspend fun deleteStockWatch(stockWatch: StockWatchEntity)
}
