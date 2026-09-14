package com.stockflip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastObservationDao {
    @Query("SELECT * FROM podcast_observations")
    suspend fun getAllEntries(): List<PodcastObservationEntity>

    @Query(
        """
        SELECT * FROM podcast_observations
        WHERE ticker = :ticker
        ORDER BY COALESCE(publishedAtMillis, storedAtMillis) DESC
        """
    )
    suspend fun getForTicker(ticker: String): List<PodcastObservationEntity>

    @Query(
        """
        SELECT * FROM podcast_observations
        WHERE ticker = :ticker
        ORDER BY COALESCE(publishedAtMillis, storedAtMillis) DESC
        """
    )
    fun getForTickerFlow(ticker: String): Flow<List<PodcastObservationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(observations: List<PodcastObservationEntity>)

    @Query("DELETE FROM podcast_observations")
    suspend fun deleteAll()

    @Query("DELETE FROM podcast_observations WHERE ticker = :ticker")
    suspend fun deleteForTicker(ticker: String)
}
