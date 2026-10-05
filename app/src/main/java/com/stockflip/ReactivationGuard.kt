package com.stockflip

/**
 * Avgör om datumspärren (lastTriggeredDate) ska behållas vid återaktivering, så att larmet
 * inte utlöses på nytt direkt av bakgrundsjobbet eller flimrar mellan utlöst/återställd i UI:t.
 *
 * Gäller alla larmtyper likadant: om larmet triggades idag spärras det (return true) om
 *  1. villkoret fortfarande är uppfyllt just nu, eller
 *  2. villkoret inte går att avgöra (data saknas) – konservativ spärr, eller
 *  3. marknaden för symbolen är stängd.
 *
 * Annars (villkoret har upphört och marknaden är öppen) släpps spärren direkt, så att larmet
 * kan utlösas igen redan vid nästa prissynk samma handelsdag, i stället för att tvinga
 * användaren att vänta till börsstängning eller nästa dag.
 */
suspend fun shouldGuardAgainstImmediateRetrigger(
    watchItem: WatchItem,
    today: String = WatchItem.getTodayDateString(),
    conditionCurrentlyMet: () -> Boolean?,
    isMarketOpen: suspend () -> Boolean
): Boolean {
    if (watchItem.lastTriggeredDate != today) return false

    when (conditionCurrentlyMet()) {
        true -> return true
        null -> return true
        false -> { /* villkoret har upphört – kontrollera marknadstid nedan */ }
    }

    return !isMarketOpen()
}

/**
 * Tidpunkt (epoch ms) före vilken en just återaktiverad bevakning inte får utlösas, eller null.
 *
 * Återaktiveras en bevakning medan börsen är stängd (eller 09:00–09:15, innan första kursen
 * kommit) är kurserna föregående stängning. De får inte utlösa bevakningen: den väntar på
 * första färska kursen efter nästa öppning. För flera symboler (par, kombinerat) gäller den
 * senaste tidpunkten. Se [StockMarketScheduler.triggerBlockedUntil].
 */
suspend fun triggerBlockedUntilForReactivation(
    watchItem: WatchItem,
    exchangeOf: suspend (String) -> String?
): Long? {
    if (watchItem.watchType is WatchType.InsiderBuy) return null
    val symbols = (listOfNotNull(watchItem.ticker, watchItem.ticker1, watchItem.ticker2) +
        (watchItem.watchType as? WatchType.Combined)?.expression?.getSymbols().orEmpty()).distinct()
    return symbols.mapNotNull { symbol ->
        StockMarketScheduler.triggerBlockedUntil(
            symbol = symbol,
            exchange = StockMarketScheduler.inferExchangeFromSymbol(symbol) ?: exchangeOf(symbol)
        )
    }.maxOrNull()
}
