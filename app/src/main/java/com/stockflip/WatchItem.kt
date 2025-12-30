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
    val companyName: String? = null
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
    var currentATH: Double = 0.0
        private set

    @Ignore
    var currentDropPercentage: Double = 0.0
        private set

    @Ignore
    var currentDropAbsolute: Double = 0.0
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

    fun withCurrentMetricValue(value: Double?): WatchItem {
        Log.d(TAG, "Updating metric value for watch item $id: $value")
        if (value == null) {
            Log.w(TAG, "Received null metric value for watch item $id")
            return this
        }
        return copy().also {
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
        }
    }

    fun getWatchTypeDisplayName(): String {
        return when (watchType) {
            is WatchType.PricePair -> "Aktiepar"
            is WatchType.PriceTarget -> "Prisbevakning"
            is WatchType.KeyMetrics -> "Nyckeltal"
            is WatchType.ATHBased -> "ATH-bevakning"
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

    companion object {
        private const val TAG = "WatchItem"
        private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))
    }
}

