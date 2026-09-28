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
