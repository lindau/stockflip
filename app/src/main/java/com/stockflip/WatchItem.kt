package com.stockflip

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Generic watch item that can represent different types of stock watches.
 * Replaces StockPair to support multiple watch types.
 *
 * Only persisted fields are stored here. Live market data (prices, metrics, etc.)
 * lives in LiveWatchData and is combined into WatchItemUiState by the ViewModel.
 */
@Entity(tableName = "watch_items")
@TypeConverters(WatchTypeConverter::class)
data class WatchItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val watchType: WatchType,
    // For price pair watches
    val ticker1: String? = null,
    val ticker2: String? = null,
    val companyName1: String? = null,
    val companyName2: String? = null,
    // For single stock watches (price target, etc.)
    val ticker: String? = null,
    val companyName: String? = null,
    // Spam protection fields (enligt PRD: max en gång per handelsdag eller markeras triggad)
    val lastTriggeredDate: String? = null, // Format: "YYYY-MM-DD"
    val isTriggered: Boolean = false, // Markerad som triggad, kräver manuell återaktivering
    val isActive: Boolean = true // Om alerten är aktiv (kan inaktiveras manuellt)
) {
    fun getDisplayName(): String {
        return when (watchType) {
            is WatchType.PricePair -> {
                "${companyName1 ?: ticker1} - ${companyName2 ?: ticker2}"
            }
            is WatchType.PriceTarget -> {
                "${companyName ?: ticker} (${ticker ?: ""})"
            }
            is WatchType.KeyMetrics -> {
                "${companyName ?: ticker} (${ticker ?: ""})"
            }
            is WatchType.ATHBased -> {
                "${companyName ?: ticker} (${ticker ?: ""})"
            }
            is WatchType.PriceRange -> {
                "${companyName ?: ticker} (${ticker ?: ""})"
            }
            is WatchType.DailyMove -> {
                "${companyName ?: ticker} (${ticker ?: ""})"
            }
            is WatchType.Combined -> {
                val symbols = watchType.expression.getSymbols()
                if (symbols.isNotEmpty()) {
                    "${symbols.first()} (kombinerat)"
                } else {
                    "Kombinerat larm"
                }
            }
        }
    }

    fun getWatchTypeDisplayName(): String {
        return watchType.kind.displayName
    }

    /**
     * Kontrollerar om alerten kan trigga baserat på spam-skydd.
     * Enligt PRD: max en gång per handelsdag eller markeras triggad tills manuellt återaktiverad.
     *
     * @param today Datum i format "YYYY-MM-DD"
     * @return true om alerten kan trigga, false annars
     */
    fun canTrigger(today: String): Boolean {
        if (!isActive) return false
        // Engångslarm (PriceTarget, ATHBased) blockeras permanent av isTriggered — kräver
        // manuell återaktivering. Övriga larmtyper är återkommande och blockeras bara av
        // datumet (ett larm per dag), inte av isTriggered-flaggan.
        val isOneTimeAlarm = watchType is WatchType.PriceTarget || watchType is WatchType.ATHBased
        if (isOneTimeAlarm && isTriggered) return false
        if (lastTriggeredDate == today) return false
        return true
    }

    /**
     * Markerar alerten som triggad för idag.
     *
     * @param today Datum i format "YYYY-MM-DD"
     * @return Ny WatchItem med uppdaterade spam-skyddsfält
     */
    fun markAsTriggered(today: String): WatchItem {
        return copy(
            lastTriggeredDate = today,
            isTriggered = true
        )
    }

    /**
     * Återaktiverar alerten (tar bort triggad-status).
     *
     * @return Ny WatchItem med isTriggered = false, lastTriggeredDate = null och isActive = true
     */
    fun reactivate(currentPrice: Double? = null): WatchItem {
        val updatedWatchType = when (val type = watchType) {
            is WatchType.PriceTarget -> {
                currentPrice
                    ?.takeIf { it > 0.0 }
                    ?.let { price ->
                        type.copy(
                            direction = if (price >= type.targetPrice) {
                                WatchType.PriceDirection.BELOW
                            } else {
                                WatchType.PriceDirection.ABOVE
                            }
                        )
                    }
                    ?: type
            }
            else -> watchType
        }
        return copy(
            watchType = updatedWatchType,
            isTriggered = false,
            lastTriggeredDate = null,
            isActive = true
        )
    }

    /**
     * Aktiverar/inaktiverar alerten.
     *
     * @param active true för att aktivera, false för att inaktivera
     * @return Ny WatchItem med uppdaterat isActive
     */
    fun setActive(active: Boolean): WatchItem {
        return copy(isActive = active)
    }

    companion object {
        /**
         * Hämtar dagens datum i format "YYYY-MM-DD".
         */
        fun getTodayDateString(): String {
            val calendar = java.util.Calendar.getInstance()
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH) + 1 // Calendar.MONTH är 0-baserad
            val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            return String.format("%04d-%02d-%02d", year, month, day)
        }
    }
}
