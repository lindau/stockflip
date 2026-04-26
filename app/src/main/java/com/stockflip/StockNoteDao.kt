package com.stockflip

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StockNoteDao {
    @Query("SELECT * FROM stock_notes")
    suspend fun getAllNotes(): List<StockNote>

    @Query("SELECT * FROM stock_notes WHERE ticker = :ticker")
    fun getByTickerFlow(ticker: String): Flow<StockNote?>

    @Upsert
    suspend fun upsert(note: StockNote)

    @Query("DELETE FROM stock_notes WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)
}
