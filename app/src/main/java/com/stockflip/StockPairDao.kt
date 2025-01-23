package com.stockflip

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StockPairDao {
    @Query("SELECT * FROM stock_pairs")
    suspend fun getAllStockPairs(): List<StockPair>

    @Insert
    suspend fun insertStockPair(pair: StockPair)

    @Update
    suspend fun update(pair: StockPair)

    @Delete
    suspend fun deleteStockPair(pair: StockPair)
} 