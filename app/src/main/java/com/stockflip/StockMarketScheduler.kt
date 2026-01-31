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

    /**
     * Kontrollerar om en specifik börs är öppen baserat på börs-kod.
     * 
     * @param exchange Börs-kod (t.ex. "STO", "NASDAQ", "NYSE", "NMS", "NYQ")
     * @return true om börsen är öppen, false annars
     */
    fun isMarketOpenForExchange(exchange: String?): Boolean {
        if (exchange == null) {
            return false
        }
        
        val exchangeUpper = exchange.uppercase()
        
        // Krypto är alltid öppet
        if (exchangeUpper.contains("CRYPTO", ignoreCase = true)) {
            return true
        }
        
        val now = LocalDateTime.now()
        val currentDay = now.dayOfWeek
        
        // Stängd på helger
        if (currentDay == DayOfWeek.SATURDAY || currentDay == DayOfWeek.SUNDAY) {
            return false
        }
        
        return when {
            // Svenska börsen (Stockholm)
            exchangeUpper == "STO" || exchangeUpper.contains("STOCKHOLM", ignoreCase = true) -> {
                val stockholmTime = now.atZone(ZoneId.of("Europe/Stockholm"))
                val localTime = stockholmTime.toLocalTime()
                localTime.isAfter(LocalTime.of(9, 0)) && localTime.isBefore(LocalTime.of(17, 30))
            }
            // Amerikanska börser (NASDAQ, NYSE, etc.)
            exchangeUpper.contains("NASDAQ", ignoreCase = true) || 
            exchangeUpper == "NMS" || 
            exchangeUpper == "NCM" || 
            exchangeUpper == "NGM" ||
            exchangeUpper.contains("NYSE", ignoreCase = true) || 
            exchangeUpper == "NYQ" || 
            exchangeUpper == "NYM" ||
            exchangeUpper == "AMEX" || 
            exchangeUpper.contains("AMERICAN", ignoreCase = true) -> {
                val usTime = now.atZone(ZoneId.of("America/New_York"))
                val localTime = usTime.toLocalTime()
                // USA börser: 09:30 - 16:00 ET
                localTime.isAfter(LocalTime.of(9, 30)) && localTime.isBefore(LocalTime.of(16, 0))
            }
            // Storbritannien (LSE)
            exchangeUpper == "LSE" || exchangeUpper.contains("LONDON", ignoreCase = true) -> {
                val londonTime = now.atZone(ZoneId.of("Europe/London"))
                val localTime = londonTime.toLocalTime()
                localTime.isAfter(LocalTime.of(8, 0)) && localTime.isBefore(LocalTime.of(16, 30))
            }
            // Tyskland (XETR, XFRA)
            exchangeUpper == "XETR" || exchangeUpper == "XFRA" || exchangeUpper.contains("XETRA", ignoreCase = true) -> {
                val germanyTime = now.atZone(ZoneId.of("Europe/Berlin"))
                val localTime = germanyTime.toLocalTime()
                localTime.isAfter(LocalTime.of(9, 0)) && localTime.isBefore(LocalTime.of(17, 30))
            }
            // Japan (TSE)
            exchangeUpper == "TSE" || exchangeUpper.contains("TOKYO", ignoreCase = true) -> {
                val tokyoTime = now.atZone(ZoneId.of("Asia/Tokyo"))
                val localTime = tokyoTime.toLocalTime()
                localTime.isAfter(LocalTime.of(9, 0)) && localTime.isBefore(LocalTime.of(15, 0))
            }
            // Norge (OSE - Oslo Stock Exchange)
            exchangeUpper == "OSE" || exchangeUpper.contains("OSLO", ignoreCase = true) -> {
                val osloTime = now.atZone(ZoneId.of("Europe/Oslo"))
                val localTime = osloTime.toLocalTime()
                localTime.isAfter(LocalTime.of(9, 0)) && localTime.isBefore(LocalTime.of(16, 25))
            }
            // Default: använd svensk börstid
            else -> {
                val stockholmTime = now.atZone(ZoneId.of("Europe/Stockholm"))
                val localTime = stockholmTime.toLocalTime()
                localTime.isAfter(LocalTime.of(9, 0)) && localTime.isBefore(LocalTime.of(17, 30))
            }
        }
    }
} 
