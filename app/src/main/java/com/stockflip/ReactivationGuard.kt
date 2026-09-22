package com.stockflip

/**
 * Avgör om datumspärren (lastTriggeredDate) ska behållas vid återaktivering, så att larmet
 * inte utlöses på nytt direkt av bakgrundsjobbet eller flimrar mellan utlöst/återställd i UI:t.
 * Se [WatchItem.hasStructuralReactivationAdjustment] för varför bara vissa typer får en
 * tidigare återväpning baserad på en live-omprövning av villkoret.
 *
 * Spärras (return true) om larmet triggades idag OCH larmtypen saknar strukturell justering
 * vid återaktivering, eller om den har det men:
 *  1. villkoret fortfarande är uppfyllt just nu, eller
 *  2. villkoret inte går att avgöra (data saknas) – konservativ spärr, eller
 *  3. marknaden för symbolen är stängd.
 */
suspend fun shouldGuardAgainstImmediateRetrigger(
    watchItem: WatchItem,
    today: String = WatchItem.getTodayDateString(),
    conditionCurrentlyMet: () -> Boolean?,
    isMarketOpen: suspend () -> Boolean
): Boolean {
    if (watchItem.lastTriggeredDate != today) return false
    if (!watchItem.hasStructuralReactivationAdjustment) return true

    when (conditionCurrentlyMet()) {
        true -> return true
        null -> return true
        false -> { /* villkoret har upphört – kontrollera marknadstid nedan */ }
    }

    return !isMarketOpen()
}
