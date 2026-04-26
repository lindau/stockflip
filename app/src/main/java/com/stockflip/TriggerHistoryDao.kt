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

    @Query("DELETE FROM trigger_history WHERE triggeredAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
