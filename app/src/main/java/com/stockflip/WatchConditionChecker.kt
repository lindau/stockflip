package com.stockflip

/**
 * Ren hjälpare för att avgöra om ett larms trigger-villkor är uppfyllt *just nu*, givet en
 * [MarketSnapshot]. Delas av återaktiveringslogiken (ViewModels) och bakgrundsjobbet.
 *
 * Skillnad mot [AlertEvaluator]: här skiljer vi på "villkoret är inte uppfyllt" (false) och
 * "det finns inte tillräckligt med data för att avgöra" (null), så att återaktivering kan
 * spärra konservativt när utfallet är okänt.
 */
object WatchConditionChecker {

    /**
     * @return true om villkoret är uppfyllt just nu, false om det inte är det,
     * null om det inte går att avgöra med tillgänglig data.
     */
    fun isConditionCurrentlyMet(item: WatchItem, snapshot: MarketSnapshot): Boolean? {
        val lastPrice = snapshot.lastPrice
        if (lastPrice == null || lastPrice <= 0.0) return null

        val rule = AlertRuleConverter.toAlertRule(item) ?: return null

        return when (rule) {
            is AlertRule.SinglePrice -> AlertEvaluator.evaluate(rule, snapshot)

            is AlertRule.SingleDrawdownFromHigh -> {
                val high = when (rule.reference) {
                    AlertRule.HighReference.FIFTY_TWO_WEEK_HIGH -> snapshot.week52High
                    AlertRule.HighReference.ALL_TIME_HIGH -> snapshot.allTimeHigh
                }
                if (high == null || high <= 0.0) null else AlertEvaluator.evaluate(rule, snapshot)
            }

            is AlertRule.SingleDailyMove ->
                if (snapshot.previousCloseOrPriceB == null) null else AlertEvaluator.evaluate(rule, snapshot)

            is AlertRule.SingleKeyMetric ->
                if (snapshot.keyMetrics[rule.metricType] == null) null else AlertEvaluator.evaluate(rule, snapshot)

            // Par-larm hanteras separat (behöver två snapshots / sidologik).
            is AlertRule.PairSpread -> null
        }
    }
}
