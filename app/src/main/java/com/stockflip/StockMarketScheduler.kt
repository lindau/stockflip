package com.stockflip

import android.util.Log
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.DayOfWeek

object StockMarketScheduler {
    private const val TAG = "StockMarketScheduler"

    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MINUTES = 1L

    /**
     * Hur länge efter ordinarie stängning ett kurslarm fortfarande får skicka
     * notis, så att den sista rörelsen vid stängning inte tappas.
     */
    const val NOTIFICATION_GRACE_MINUTES = 30L

    /** Fördröjning från börsöppning tills appen får första färska kursen (09:00 → ca 09:15). */
    const val QUOTE_DELAY_MINUTES = 15L

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
            StockSearchResult.isIndexSymbol(upperSymbol) -> StockSearchResult.indexExchange(upperSymbol)
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
        // Kända index mappas på symbol: Yahoo anger t.ex. ^OMXS30 med börskod NIM (Nasdaq)
        val indexExchange = symbol?.let(StockSearchResult::indexExchange)
        return isMarketOpenForExchange(indexExchange ?: exchange ?: inferExchangeFromSymbol(symbol, currency), instant)
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
            val resolvedExchange = StockSearchResult.indexExchange(symbol) ?: exchange ?: inferExchangeFromSymbol(symbol)
            when {
                StockSearchResult.isCryptoSymbol(symbol) -> true
                resolvedExchange == null -> true // fail-open på okänd börs
                else -> isWithinNotificationWindowForExchange(resolvedExchange, instant)
            }
        }
    }

    private data class Session(val zone: ZoneId, val opensAt: LocalTime, val closesAt: LocalTime)

    /** Handelspass (tidszon, öppning, stängning) per börs. Null för krypto (alltid öppet) och null-börs. */
    private fun sessionFor(exchange: String?): Session? {
        if (exchange == null) return null
        val exchangeUpper = exchange.uppercase()
        if (exchangeUpper.contains("CRYPTO", ignoreCase = true)) return null
        return when {
            // Svenska börsen (Stockholm)
            exchangeUpper == "STO" || exchangeUpper.contains("STOCKHOLM", ignoreCase = true) ->
                Session(ZoneId.of("Europe/Stockholm"), LocalTime.of(9, 0), LocalTime.of(17, 30))
            // Amerikanska börser (NASDAQ, NYSE, etc.): 09:30 - 16:00 ET
            exchangeUpper.contains("NASDAQ", ignoreCase = true) ||
            exchangeUpper == "NMS" ||
            exchangeUpper == "NCM" ||
            exchangeUpper == "NGM" ||
            exchangeUpper.contains("NYSE", ignoreCase = true) ||
            exchangeUpper == "NYQ" ||
            exchangeUpper == "NYM" ||
            exchangeUpper == "AMEX" ||
            // Yahoos börskoder för amerikanska index (^GSPC, ^DJI, ^VIX). NIM utelämnas: används även för ^OMXS30.
            exchangeUpper == "SNP" ||
            exchangeUpper == "DJI" ||
            exchangeUpper == "WCB" ||
            exchangeUpper == "CXI" ||
            exchangeUpper.contains("AMERICAN", ignoreCase = true) ->
                Session(ZoneId.of("America/New_York"), LocalTime.of(9, 30), LocalTime.of(16, 0))
            // Storbritannien (LSE)
            exchangeUpper == "LSE" || exchangeUpper == "FGI" || exchangeUpper.contains("LONDON", ignoreCase = true) ->
                Session(ZoneId.of("Europe/London"), LocalTime.of(8, 0), LocalTime.of(16, 30))
            // Tyskland (XETR, XFRA)
            exchangeUpper == "XETR" || exchangeUpper == "XFRA" || exchangeUpper == "GER" || exchangeUpper.contains("XETRA", ignoreCase = true) ->
                Session(ZoneId.of("Europe/Berlin"), LocalTime.of(9, 0), LocalTime.of(17, 30))
            // Japan (TSE)
            exchangeUpper == "TSE" || exchangeUpper.contains("TOKYO", ignoreCase = true) ->
                Session(ZoneId.of("Asia/Tokyo"), LocalTime.of(9, 0), LocalTime.of(15, 0))
            // Norge (OSE - Oslo Stock Exchange)
            exchangeUpper == "OSE" || exchangeUpper.contains("OSLO", ignoreCase = true) ->
                Session(ZoneId.of("Europe/Oslo"), LocalTime.of(9, 0), LocalTime.of(16, 25))
            // Default: använd svensk börstid
            else -> Session(ZoneId.of("Europe/Stockholm"), LocalTime.of(9, 0), LocalTime.of(17, 30))
        }
    }

    private fun evaluateExchangeWindow(
        exchange: String?,
        instant: Instant,
        graceMinutes: Long
    ): Boolean {
        if (exchange == null) return false
        // Krypto är alltid öppet
        if (exchange.contains("CRYPTO", ignoreCase = true)) return true
        val session = sessionFor(exchange) ?: return false
        return isOpenInZone(instant, session.zone, session.opensAt, session.closesAt, graceMinutes)
    }

    /**
     * Tidpunkt (epoch ms) före vilken en återaktiverad bevakning inte får utlösas, eller null
     * om ingen spärr behövs. Spärr behövs när börsen är stängd just nu: kurserna är då
     * gamla (föregående stängning) och kommer först [QUOTE_DELAY_MINUTES] minuter efter nästa
     * öppning (börsen öppnar 09:00 men appen får första kursen ca 09:15). Öppen börs, krypto
     * och okänd börs ger null.
     */
    fun triggerBlockedUntil(
        symbol: String?,
        exchange: String? = null,
        currency: String? = null,
        instant: Instant = Instant.now()
    ): Long? {
        if (symbol != null && StockSearchResult.isCryptoSymbol(symbol)) return null
        val resolved = symbol?.let(StockSearchResult::indexExchange)
            ?: exchange ?: inferExchangeFromSymbol(symbol, currency) ?: return null
        val session = sessionFor(resolved) ?: return null
        val now = LocalDateTime.ofInstant(instant, session.zone)
        var day = now.toLocalDate()
        fun isWeekend(d: java.time.LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
        val firstQuoteToday = day.atTime(session.opensAt).plusMinutes(QUOTE_DELAY_MINUTES)
        if (!isWeekend(day) && now.isBefore(firstQuoteToday)) {
            // Före öppning, eller 09:00–09:15 då kurserna fortfarande är gamla: spärra till idag 09:15.
        } else if (isOpenInZone(instant, session.zone, session.opensAt, session.closesAt, 0L)) {
            return null
        } else {
            day = day.plusDays(1)
            while (isWeekend(day)) day = day.plusDays(1)
        }
        return day.atTime(session.opensAt).plusMinutes(QUOTE_DELAY_MINUTES)
            .atZone(session.zone).toInstant().toEpochMilli()
    }

    /**
     * Är kursen (senaste affärens tid, [quoteEpochSeconds]) från den senaste handelsdag som
     * borde ha kurser? Förväntad dag är idag om börsen öppnat och första kursen hunnit komma
     * ([QUOTE_DELAY_MINUTES] efter öppning), annars föregående vardag. En aktie som inte
     * handlats sedan en tidigare dag (illikvid, eller helgdag) ger false: Yahoo behåller då
     * gårdagens eller äldre dagsrörelse i svaret, och den får inte tolkas som "idag".
     *
     * @return null om börsen är okänd (fail-open); krypto är alltid true.
     */
    fun isQuoteFromLatestSession(
        symbol: String?,
        exchange: String?,
        currency: String?,
        quoteEpochSeconds: Long,
        instant: Instant = Instant.now()
    ): Boolean? {
        if (symbol != null && StockSearchResult.isCryptoSymbol(symbol)) return true
        val resolved = symbol?.let(StockSearchResult::indexExchange)
            ?: exchange ?: inferExchangeFromSymbol(symbol, currency) ?: return null
        if (resolved.contains("CRYPTO", ignoreCase = true)) return true
        val session = sessionFor(resolved) ?: return null
        val now = LocalDateTime.ofInstant(instant, session.zone)
        var expected = now.toLocalDate()
        fun isWeekend(d: java.time.LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
        val firstQuoteToday = expected.atTime(session.opensAt).plusMinutes(QUOTE_DELAY_MINUTES)
        if (isWeekend(expected) || now.isBefore(firstQuoteToday)) {
            do { expected = expected.minusDays(1) } while (isWeekend(expected))
        }
        val quoteDate = Instant.ofEpochSecond(quoteEpochSeconds).atZone(session.zone).toLocalDate()
        return !quoteDate.isBefore(expected)
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
