package com.stockflip

import android.util.Log
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.DayOfWeek

object StockMarketScheduler {
    private const val TAG = "StockMarketScheduler"
    private val stockholmZone = ZoneId.of("Europe/Stockholm")
    
    private val marketOpen = LocalTime.of(9, 0)
    private val marketClose = LocalTime.of(17, 0)
    
    const val MARKET_HOURS_INTERVAL_MINUTES = 1L
    const val AFTER_HOURS_INTERVAL_MINUTES = 60L
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MINUTES = 1L

    fun isMarketOpen(): Boolean {
        val now = LocalDateTime.now(stockholmZone)
        val currentTime = now.toLocalTime()
        val currentDay = now.dayOfWeek
        
        // Stängd på helger
        if (currentDay == DayOfWeek.SATURDAY || currentDay == DayOfWeek.SUNDAY) {
            Log.d(TAG, "Market is closed (weekend)")
            return false
        }
        
        // Kontrollera öppettider på vardagar
        val isOpenNow = currentTime.isAfter(marketOpen) && currentTime.isBefore(marketClose)
        Log.d(TAG, if (isOpenNow) "Market is open (weekday during trading hours)" 
                   else "Market is closed (weekday outside trading hours)")
        return isOpenNow
    }

    fun getUpdateInterval(): Long {
        return if (isMarketOpen()) {
            Log.d(TAG, "Using ${MARKET_HOURS_INTERVAL_MINUTES} minute interval (market open)")
            MARKET_HOURS_INTERVAL_MINUTES
        } else {
            Log.d(TAG, "Using ${AFTER_HOURS_INTERVAL_MINUTES} minute interval (market closed)")
            AFTER_HOURS_INTERVAL_MINUTES
        }
    }

    fun shouldRetry(attempt: Int, error: Exception): Boolean {
        val shouldRetry = attempt < MAX_RETRY_ATTEMPTS
        Log.d(TAG, "Price update failed (attempt $attempt): ${error.message}. Will${if (!shouldRetry) " not" else ""} retry")
        return shouldRetry
    }
} 
