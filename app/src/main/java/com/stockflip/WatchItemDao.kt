package com.stockflip

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchItemDao {
    @Query("SELECT * FROM watch_items")
    suspend fun getAllWatchItems(): List<WatchItem>

    @Query("SELECT * FROM watch_items")
    fun getAllWatchItemsFlow(): Flow<List<WatchItem>>

    @Query("SELECT * FROM watch_items WHERE ticker = :symbol OR ticker1 = :symbol OR ticker2 = :symbol")
    fun getWatchItemsBySymbolFlow(symbol: String): Flow<List<WatchItem>>

    @Insert
    suspend fun insertWatchItem(item: WatchItem)

    @Update
    suspend fun update(item: WatchItem)

    @Delete
    suspend fun deleteWatchItem(item: WatchItem)

    @Query("SELECT * FROM watch_items WHERE id = :id")
    suspend fun getWatchItemById(id: Int): WatchItem?
}

