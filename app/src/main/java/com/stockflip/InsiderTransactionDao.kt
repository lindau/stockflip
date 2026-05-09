package com.stockflip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface InsiderTransactionDao {
    @Query("SELECT * FROM insider_transactions")
    suspend fun getAllEntries(): List<InsiderTransactionEntity>

    @Query("SELECT id FROM insider_transactions WHERE id IN (:ids)")
    suspend fun getExistingIds(ids: List<String>): List<String>

    @Query(
        """
        SELECT * FROM insider_transactions
        WHERE symbol = :symbol
        ORDER BY COALESCE(acceptedAtMillis, storedAtMillis) DESC
        LIMIT :limit
        """
    )
    suspend fun getLatestForSymbol(symbol: String, limit: Int = 20): List<InsiderTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<InsiderTransactionEntity>)

    @Query("DELETE FROM insider_transactions")
    suspend fun deleteAll()
}
