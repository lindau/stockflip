// StockPairDao.kt
@Dao
interface StockPairDao {
    @Query("SELECT * FROM stock_pairs")
    fun getAllStockPairs(): Flow<List<StockPairEntity>>
    
    @Insert
    suspend fun insertStockPair(pair: StockPairEntity)
    
    @Delete
    suspend fun deleteStockPair(pair: StockPairEntity)
}