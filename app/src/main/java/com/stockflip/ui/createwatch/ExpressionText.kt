package com.stockflip.ui.createwatch

import com.stockflip.AlertExpression
import com.stockflip.AlertRule
import com.stockflip.ui.components.formatNumber
import kotlin.math.floor

private const val MINUS = "−"

private fun num(v: Double) = formatNumber(v, if (v == floor(v)) 0 else 2)

private fun above(c: AlertRule.PriceComparisonType) = if (c == AlertRule.PriceComparisonType.ABOVE) "över" else "under"

private fun ruleText(rule: AlertRule): String = when (rule) {
    is AlertRule.SinglePrice ->
        if (rule.comparisonType == AlertRule.PriceComparisonType.WITHIN_RANGE)
            "Pris mellan ${num(rule.priceLimit)} och ${num(rule.maxPrice ?: rule.priceLimit)} kr"
        else "Pris ${above(rule.comparisonType)} ${num(rule.priceLimit)} kr"
    is AlertRule.SingleDrawdownFromHigh -> {
        val ref = if (rule.reference == AlertRule.HighReference.ALL_TIME_HIGH) "all-time-high" else "52v-högsta"
        val amount = if (rule.dropType == AlertRule.DrawdownDropType.PERCENTAGE) "${num(rule.dropValue)} %" else "${num(rule.dropValue)} kr"
        "Från $ref $MINUS$amount"
    }
    is AlertRule.SingleDailyMove -> when (rule.direction) {
        AlertRule.DailyMoveDirection.UP -> "Dagsrörelse över +${num(rule.percentThreshold)} %"
        AlertRule.DailyMoveDirection.DOWN -> "Dagsrörelse under $MINUS${num(rule.percentThreshold)} %"
        AlertRule.DailyMoveDirection.BOTH -> "Dagsrörelse ±${num(rule.percentThreshold)} %"
    }
    is AlertRule.SingleKeyMetric -> {
        val name = when (rule.metricType) {
            AlertRule.KeyMetricType.PE_RATIO -> "P/E"
            AlertRule.KeyMetricType.PS_RATIO -> "P/S"
            AlertRule.KeyMetricType.DIVIDEND_YIELD -> "Direktavkastning"
            AlertRule.KeyMetricType.EARNINGS_PER_SHARE -> "Vinst per aktie"
        }
        "$name ${above(rule.direction)} ${num(rule.targetValue)}"
    }
    is AlertRule.SinglePriceVsSma -> "Pris ${above(rule.direction)} SMA ${rule.period}"
    is AlertRule.SingleSmaCrossover -> "SMA ${rule.shortPeriod} ${above(rule.direction)} SMA ${rule.longPeriod}"
    is AlertRule.PairSpread -> "Prisskillnad ${num(rule.spreadTarget)} kr"
}

/** Klartext för ett kombinerat uttryck, t.ex. "Pris över 430 kr OCH P/E under 8". Grupper med annan operator parentesomsluts. */
internal fun describeExpression(expression: AlertExpression): String {
    fun render(e: AlertExpression, parent: String?): String = when (e) {
        is AlertExpression.Single -> ruleText(e.rule)
        is AlertExpression.Not -> "inte (${render(e.inner, null)})"
        is AlertExpression.And -> group(render(e.left, "OCH"), render(e.right, "OCH"), "OCH", parent)
        is AlertExpression.Or -> group(render(e.left, "ELLER"), render(e.right, "ELLER"), "ELLER", parent)
    }
    return render(expression, null)
}

private fun group(left: String, right: String, op: String, parent: String?): String {
    val text = "$left $op $right"
    return if (parent != null && parent != op) "($text)" else text
}
