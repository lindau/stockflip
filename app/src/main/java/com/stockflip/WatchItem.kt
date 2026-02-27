package com.stockflip

import android.util.Log
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore
import androidx.room.TypeConverters
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Generic watch item that can represent different types of stock watches.
 * Replaces StockPair to support multiple watch types.
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
    @Ignore
    var currentPrice1: Double = 0.0
        private set

    @Ignore
    var currentPrice2: Double = 0.0
        private set

    @Ignore
    var currentPrice: Double = 0.0
        private set

    @Ignore
    var currentMetricValue: Double = 0.0
        private set

    @Ignore
    var metricValueAtCreation: Double = 0.0
        private set

    @Ignore
    var currentATH: Double = 0.0
        private set

    @Ignore
    var currentDropPercentage: Double = 0.0
        private set

    @Ignore
    var currentDropAbsolute: Double = 0.0
        private set

    @Ignore
    var currentDailyChangePercent: Double? = null
        private set

    fun withCurrentPrices(price1: Double?, price2: Double?): WatchItem {
        Log.d(TAG, "Updating prices for watch item $id: $price1, $price2")
        if (price1 == null || price2 == null) {
            Log.w(TAG, "Received null prices for watch item $id")
            return this
        }
        return copy().also {
            it.currentPrice1 = price1
            it.currentPrice2 = price2
            Log.d(TAG, "Updated prices for watch item $id: ${it.currentPrice1}, ${it.currentPrice2}")
        }
    }

    fun withCurrentPrice(price: Double?): WatchItem {
        Log.d(TAG, "Updating price for watch item $id: $price")
        if (price == null) {
            Log.w(TAG, "Received null price for watch item $id")
            return this
        }
        return copy().also {
            it.currentPrice = price
            Log.d(TAG, "Updated price for watch item $id: ${it.currentPrice}")
        }
    }

    fun withCurrentPriceAndDailyChange(price: Double?, changePercent: Double?): WatchItem {
        Log.d(TAG, "Updating price and daily change for watch item $id: price=$price, changePercent=$changePercent")
        if (price == null) {
            Log.w(TAG, "Received null price for watch item $id")
            return this
        }
        return copy().also {
            it.currentPrice = price
            it.currentDailyChangePercent = changePercent
            Log.d(TAG, "Updated price and daily change for watch item $id: ${it.currentPrice}, ${it.currentDailyChangePercent}%")
        }
    }

    fun withDailyChangePercent(changePercent: Double?): WatchItem {
        return copy().also {
            it.currentDailyChangePercent = changePercent
        }
    }

    fun withCurrentMetricValue(value: Double?): WatchItem {
        Log.d(TAG, "Updating metric value for watch item $id: $value")
        if (value == null) {
            Log.w(TAG, "Received null metric value for watch item $id")
            return this
        }
        return copy().also {
            // Om metricValueAtCreation är 0, spara första värdet som "värde vid skapande"
            if (it.metricValueAtCreation == 0.0 && value > 0.0) {
                it.metricValueAtCreation = value
                Log.d(TAG, "Set metric value at creation for watch item $id: ${it.metricValueAtCreation}")
            }
            it.currentMetricValue = value
            Log.d(TAG, "Updated metric value for watch item $id: ${it.currentMetricValue}")
        }
    }

    fun withATHData(ath: Double?, currentPrice: Double?): WatchItem {
        Log.d(TAG, "Updating ATH data for watch item $id: ATH=$ath, Price=$currentPrice")
        if (ath == null || currentPrice == null || ath <= 0 || currentPrice <= 0) {
            Log.w(TAG, "Received invalid ATH data for watch item $id")
            return this
        }
        return copy().also {
            it.currentATH = ath
            it.currentPrice = currentPrice
            it.currentDropPercentage = ((ath - currentPrice) / ath) * 100
            it.currentDropAbsolute = ath - currentPrice
            Log.d(TAG, "Updated ATH data for watch item $id: ATH=${it.currentATH}, Drop=${it.currentDropPercentage}%, ${it.currentDropAbsolute} SEK")
        }
    }

    fun formatPrice1(): String = formatPrice(currentPrice1)

    fun formatPrice2(): String = formatPrice(currentPrice2)

    fun formatPrice(): String = formatPrice(currentPrice)

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
                // För Combined kan vi visa första symbolen eller en generisk beskrivning
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
        return when (watchType) {
            is WatchType.PricePair -> "Aktiepar"
            is WatchType.PriceTarget -> "Prisbevakning"
            is WatchType.KeyMetrics -> "Nyckeltal"
            is WatchType.ATHBased -> "52-veckorshögsta"
            is WatchType.PriceRange -> "Prisintervall"
            is WatchType.DailyMove -> "Dagsrörelse"
            is WatchType.Combined -> "Kombinerat larm"
        }
    }

    fun formatATHDrop(): String {
        return when (watchType) {
            is WatchType.ATHBased -> {
                when (watchType.dropType) {
                    WatchType.DropType.PERCENTAGE -> {
                        if (currentDropPercentage > 0.0) "${priceFormat.format(currentDropPercentage)}%" else "Loading..."
                    }
                    WatchType.DropType.ABSOLUTE -> {
                        if (currentDropAbsolute > 0.0) "${priceFormat.format(currentDropAbsolute)} SEK" else "Loading..."
                    }
                }
            }
            else -> ""
        }
    }

    fun formatMetricValue(): String {
        return when (watchType) {
            is WatchType.KeyMetrics -> {
                when (watchType.metricType) {
                    WatchType.MetricType.DIVIDEND_YIELD -> 
                        if (currentMetricValue > 0.0) "${priceFormat.format(currentMetricValue)}%" else "Loading..."
                    else -> 
                        if (currentMetricValue > 0.0) priceFormat.format(currentMetricValue) else "Loading..."
                }
            }
            else -> ""
        }
    }

    private fun formatPrice(price: Double): String =
        if (price > 0.0) "${priceFormat.format(price)} SEK" else "Loading..."

    /**
     * Kontrollerar om alerten kan trigga baserat på spam-skydd.
     * Enligt PRD: max en gång per handelsdag eller markeras triggad tills manuellt återaktiverad.
     * 
     * @param today Datum i format "YYYY-MM-DD"
     * @return true om alerten kan trigga, false annars
     */
    fun canTrigger(today: String): Boolean {
        // Om alerten är inaktiverad, kan den inte trigga
        if (!isActive) {
            return false
        }
        
        // Om alerten är markerad som triggad, krävs manuell återaktivering
        if (isTriggered) {
            return false
        }
        
        // Om alerten redan triggade idag, skippa
        if (lastTriggeredDate == today) {
            return false
        }
        
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
     * @return Ny WatchItem med isTriggered = false
     */
    fun reactivate(): WatchItem {
        return copy(isTriggered = false)
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
        private const val TAG = "WatchItem"
        private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))
        
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

