package com.stockflip

import android.util.Log
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.DayOfWeek

object StockMarketScheduler {
    private const val TAG = "StockMarketScheduler"
    private val stockholmZone = ZoneId.of("Europe/Stockholm")
    
    private val marketOpen = LocalTime.of(9, 0)
    private val marketClose = LocalTime.of(17, 30)

    const val MARKET_HOURS_INTERVAL_MINUTES = 1L
    const val AFTER_HOURS_INTERVAL_MINUTES = 60L
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MINUTES = 1L

    /**
     * Hur länge efter ordinarie stängning ett kurslarm fortfarande får skicka
     * notis, så att den sista rörelsen vid stängning inte tappas.
     */
    const val NOTIFICATION_GRACE_MINUTES = 30L

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
    fun inferExchangeFromSymbol(symbol: String?, currency: String? = null): String? {
        val upperSymbol = symbol?.uppercase() ?: return null
        return when {
            StockSearchResult.isCryptoSymbol(upperSymbol) -> "CRYPTO"
            upperSymbol.endsWith(".ST") || upperSymbol.endsWith(".STO") -> "STO"
            upperSymbol.endsWith(".OL") || upperSymbol.endsWith(".OSE") -> "OSE"
            upperSymbol.endsWith(".L") -> "LSE"
            upperSymbol.endsWith(".DE") || upperSymbol.endsWith(".XETR") -> "XETR"
            upperSymbol.endsWith(".T") -> "TSE"
            !upperSymbol.contains(".") && currency?.uppercase() == "USD" -> "NASDAQ"
            else -> null
        }
    }

    fun isMarketOpenForSymbol(
        symbol: String?,
        exchange: String? = null,
        currency: String? = null,
        instant: Instant = Instant.now()
    ): Boolean {
        if (symbol != null && StockSearchResult.isCryptoSymbol(symbol)) return true
        return isMarketOpenForExchange(exchange ?: inferExchangeFromSymbol(symbol, currency), instant)
    }

    fun isMarketOpenForExchange(exchange: String?, instant: Instant = Instant.now()): Boolean {
        return evaluateExchangeWindow(exchange, instant, graceMinutes = 0L)
    }

    /**
     * Som [isMarketOpenForExchange] men släpper även igenom upp till
     * [NOTIFICATION_GRACE_MINUTES] efter ordinarie stängning. Används för att
     * grinda kurslarms-notiser så de inte skickas nattetid på fryst data.
     */
    fun isWithinNotificationWindowForExchange(exchange: String?, instant: Instant = Instant.now()): Boolean {
        return evaluateExchangeWindow(exchange, instant, graceMinutes = NOTIFICATION_GRACE_MINUTES)
    }

    /**
     * Avgör om en bevaknings notiser får skickas just nu: sant om bevakningen
     * saknar kända symboler, om någon symbol är krypto, om någon symbols börs är
     * okänd (fail-open – hellre en notis för mycket än att tyst mörklägga larm),
     * eller om någon symbols börs är inom notifieringsfönstret.
     *
     * @param symbolsToExchange symbol → känd börs-kod (null om okänd).
     */
    fun isAnyRelevantMarketOpen(
        symbolsToExchange: Map<String, String?>,
        instant: Instant = Instant.now()
    ): Boolean {
        if (symbolsToExchange.isEmpty()) return true
        return symbolsToExchange.any { (symbol, exchange) ->
            val resolvedExchange = exchange ?: inferExchangeFromSymbol(symbol)
            when {
                StockSearchResult.isCryptoSymbol(symbol) -> true
                resolvedExchange == null -> true // fail-open på okänd börs
                else -> isWithinNotificationWindowForExchange(resolvedExchange, instant)
            }
        }
    }

    private fun evaluateExchangeWindow(
        exchange: String?,
        instant: Instant,
        graceMinutes: Long
    ): Boolean {
        if (exchange == null) {
            return false
        }

        val exchangeUpper = exchange.uppercase()

        // Krypto är alltid öppet
        if (exchangeUpper.contains("CRYPTO", ignoreCase = true)) {
            return true
        }

        return when {
            // Svenska börsen (Stockholm)
            exchangeUpper == "STO" || exchangeUpper.contains("STOCKHOLM", ignoreCase = true) -> {
                isOpenInZone(instant, ZoneId.of("Europe/Stockholm"), LocalTime.of(9, 0), LocalTime.of(17, 30), graceMinutes)
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
                // USA börser: 09:30 - 16:00 ET
                isOpenInZone(instant, ZoneId.of("America/New_York"), LocalTime.of(9, 30), LocalTime.of(16, 0), graceMinutes)
            }
            // Storbritannien (LSE)
            exchangeUpper == "LSE" || exchangeUpper.contains("LONDON", ignoreCase = true) -> {
                isOpenInZone(instant, ZoneId.of("Europe/London"), LocalTime.of(8, 0), LocalTime.of(16, 30), graceMinutes)
            }
            // Tyskland (XETR, XFRA)
            exchangeUpper == "XETR" || exchangeUpper == "XFRA" || exchangeUpper.contains("XETRA", ignoreCase = true) -> {
                isOpenInZone(instant, ZoneId.of("Europe/Berlin"), LocalTime.of(9, 0), LocalTime.of(17, 30), graceMinutes)
            }
            // Japan (TSE)
            exchangeUpper == "TSE" || exchangeUpper.contains("TOKYO", ignoreCase = true) -> {
                isOpenInZone(instant, ZoneId.of("Asia/Tokyo"), LocalTime.of(9, 0), LocalTime.of(15, 0), graceMinutes)
            }
            // Norge (OSE - Oslo Stock Exchange)
            exchangeUpper == "OSE" || exchangeUpper.contains("OSLO", ignoreCase = true) -> {
                isOpenInZone(instant, ZoneId.of("Europe/Oslo"), LocalTime.of(9, 0), LocalTime.of(16, 25), graceMinutes)
            }
            // Default: använd svensk börstid
            else -> {
                isOpenInZone(instant, ZoneId.of("Europe/Stockholm"), LocalTime.of(9, 0), LocalTime.of(17, 30), graceMinutes)
            }
        }
    }

    private fun isOpenInZone(
        instant: Instant,
        zoneId: ZoneId,
        opensAt: LocalTime,
        closesAt: LocalTime,
        graceMinutes: Long = 0L
    ): Boolean {
        val localDateTime = LocalDateTime.ofInstant(instant, zoneId)
        val currentDay = localDateTime.dayOfWeek
        if (currentDay == DayOfWeek.SATURDAY || currentDay == DayOfWeek.SUNDAY) {
            return false
        }
        val localTime = localDateTime.toLocalTime()
        val effectiveClose = closesAt.plusMinutes(graceMinutes)
        // plusMinutes kan rulla över midnatt; hantera bara det icke-rullande fallet
        // (grace på 30 min gör aldrig det för någon av börstiderna ovan).
        return localTime.isAfter(opensAt) && localTime.isBefore(effectiveClose)
    }
} 
