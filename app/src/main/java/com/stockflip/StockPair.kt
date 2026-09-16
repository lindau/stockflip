package com.stockflip

import android.util.Log
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore

@Entity(tableName = "stock_pairs")
data class StockPair(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ticker1: String,
    val ticker2: String,
    val companyName1: String,
    val companyName2: String,
    val priceDifference: Double = 0.0,
    val notifyWhenEqual: Boolean = false
) {
    @Ignore
    var currentPrice1: Double = 0.0
        private set

    @Ignore
    var currentPrice2: Double = 0.0
        private set

    fun withCurrentPrices(price1: Double?, price2: Double?): StockPair {
        Log.d(TAG, "Updating prices for $ticker1-$ticker2: $price1, $price2")
        if (price1 == null || price2 == null) {
            Log.w(TAG, "Received null prices for $ticker1-$ticker2")
            return this
        }
        return copy().also {
            it.currentPrice1 = price1
            it.currentPrice2 = price2
            Log.d(TAG, "Updated prices for $ticker1-$ticker2: ${it.currentPrice1}, ${it.currentPrice2}")
        }
    }

    fun getDisplayName(): String = "$companyName1 - $companyName2"

    companion object {
        private const val TAG = "StockPair"
    }
} 