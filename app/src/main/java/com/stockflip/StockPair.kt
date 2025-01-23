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
    val priceDifference: Double,
    val notifyWhenEqual: Boolean
) {
    @Ignore
    var currentPrice1: Double = 0.0
        private set

    @Ignore
    var currentPrice2: Double = 0.0
        private set

    fun withCurrentPrices(price1: Double?, price2: Double?): StockPair {
        if (price1 == null || price2 == null) {
            Log.w("StockPair", "Received null prices for $companyName1 or $companyName2")
            return this
        }
        
        return copy(
            id = id,
            ticker1 = ticker1,
            ticker2 = ticker2,
            companyName1 = companyName1,
            companyName2 = companyName2,
            priceDifference = priceDifference,
            notifyWhenEqual = notifyWhenEqual
        ).also {
            it.currentPrice1 = price1
            it.currentPrice2 = price2
            Log.d("StockPair", "Updated prices for $companyName1: $price1, $companyName2: $price2")
        }
    }

    fun getDisplayPair(): String = "$companyName1 - $companyName2"
    
    fun getFormattedPrice1() = String.format("%.2f SEK", currentPrice1)
    fun getFormattedPrice2() = String.format("%.2f SEK", currentPrice2)
    
    fun getFormattedPriceDifference() = String.format("%.2f", priceDifference)
} 