package com.stockflip

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity för att lagra historik om när en bevakning utlöstes.
 *
 * Varje rad representerar ett utlösningstillfälle för en bevakning.
 */
@Entity(
    tableName = "trigger_history",
    indices = [
        Index(value = ["watchItemId"]),
        Index(value = ["triggeredAt"])
    ]
)
data class TriggerHistoryEntity(
    @PrimaryKey val id: String, // Format: "WATCHITEM_ID_TIMESTAMP"
    val watchItemId: Int,
    val triggeredAt: Long, // System.currentTimeMillis()
)
