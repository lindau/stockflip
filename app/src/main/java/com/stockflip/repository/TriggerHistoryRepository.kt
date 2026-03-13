package com.stockflip.repository

import com.stockflip.TriggerHistoryDao
import com.stockflip.TriggerHistoryEntity
import java.util.concurrent.TimeUnit

class TriggerHistoryRepository(private val dao: TriggerHistoryDao) {

    /**
     * Sparar ett utlösningstillfälle för en bevakning med aktuell tidsstämpel.
     */
    suspend fun record(watchItemId: Int) {
        val now = System.currentTimeMillis()
        dao.insert(
            TriggerHistoryEntity(
                id = "${watchItemId}_$now",
                watchItemId = watchItemId,
                triggeredAt = now,
            )
        )
    }

    /**
     * Hämtar de senaste utlösningstidsstämplarna för en bevakning, nyast först.
     */
    suspend fun getLatest(watchItemId: Int, limit: Int = 5): List<Long> {
        return dao.getLatest(watchItemId, limit).map { it.triggeredAt }
    }

    /**
     * Rensar poster äldre än 2 år. Körs sällan (t.ex. vid appstart).
     */
    suspend fun cleanup() {
        val twoYearsAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2 * 365)
        dao.deleteOlderThan(twoYearsAgo)
    }
}
