package com.stockflip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TriggerHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TriggerHistoryEntity)

    @Query("SELECT * FROM trigger_history")
    suspend fun getAllEntries(): List<TriggerHistoryEntity>

    @Query("SELECT * FROM trigger_history WHERE watchItemId = :id ORDER BY triggeredAt DESC LIMIT :limit")
    suspend fun getLatest(id: Int, limit: Int = 5): List<TriggerHistoryEntity>

    /** Senaste utlösningen per bevakning (för "utlöst 09:14" i listan). */
    @Query("SELECT id, watchItemId, MAX(triggeredAt) AS triggeredAt FROM trigger_history GROUP BY watchItemId")
    suspend fun getLatestPerWatchItem(): List<TriggerHistoryEntity>

    @Query("DELETE FROM trigger_history WHERE triggeredAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
