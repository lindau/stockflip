package com.stockflip.ui.createwatch

import com.stockflip.AlertExpression
import com.stockflip.AlertRule
import com.stockflip.parseDecimal

/** Villkorstyper i den kombinerade byggaren (alla gäller samma aktie). */
internal enum class ConditionKind(val label: String, val hasDirection: Boolean = false) {
    PRICE("Pris", hasDirection = true),
    DRAWDOWN("Fall från högsta (%)"),
    DAILY_MOVE("Dagsrörelse (%)"),
    PE("P/E", hasDirection = true),
    PS("P/S", hasDirection = true),
    DIVIDEND("Direktavkastning", hasDirection = true),
    EPS("Vinst/aktie", hasDirection = true),
}

/** Ett villkor i listan. [or] anger hur det kopplas till föregående (ELLER, annars OCH); ignoreras för det första. */
internal data class ConditionDraft(
    val kind: ConditionKind = ConditionKind.PRICE,
    val above: Boolean = true,
    val reference: AlertRule.HighReference = AlertRule.HighReference.FIFTY_TWO_WEEK_HIGH,
    val value: String = "",
    val or: Boolean = false,
)

internal sealed class CombinedResult {
    data class Ok(val expression: AlertExpression) : CombinedResult()
    data class Invalid(val message: String) : CombinedResult()
}

private fun comparison(above: Boolean) =
    if (above) AlertRule.PriceComparisonType.ABOVE else AlertRule.PriceComparisonType.BELOW

private fun ruleFor(symbol: String, c: ConditionDraft, value: Double): AlertRule = when (c.kind) {
    ConditionKind.PRICE -> AlertRule.SinglePrice(symbol, comparison(c.above), value)
    ConditionKind.DRAWDOWN -> AlertRule.SingleDrawdownFromHigh(symbol, AlertRule.DrawdownDropType.PERCENTAGE, value, c.reference)
    ConditionKind.DAILY_MOVE -> AlertRule.SingleDailyMove(symbol, value, AlertRule.DailyMoveDirection.BOTH)
    ConditionKind.PE -> AlertRule.SingleKeyMetric(symbol, AlertRule.KeyMetricType.PE_RATIO, value, comparison(c.above))
    ConditionKind.PS -> AlertRule.SingleKeyMetric(symbol, AlertRule.KeyMetricType.PS_RATIO, value, comparison(c.above))
    ConditionKind.DIVIDEND -> AlertRule.SingleKeyMetric(symbol, AlertRule.KeyMetricType.DIVIDEND_YIELD, value, comparison(c.above))
    ConditionKind.EPS -> AlertRule.SingleKeyMetric(symbol, AlertRule.KeyMetricType.EARNINGS_PER_SHARE, value, comparison(c.above))
}

/** Bygger ett vänsterassociativt uttryck (a OCH b ELLER c = (a OCH b) ELLER c) av [conditions]. */
internal fun buildCombined(symbol: String, conditions: List<ConditionDraft>): CombinedResult {
    if (symbol.isBlank()) return CombinedResult.Invalid("Välj en aktie")
    if (conditions.isEmpty()) return CombinedResult.Invalid("Lägg till minst ett villkor")
    val rules = conditions.map { c ->
        val v = c.value.trim().parseDecimal()?.takeIf { it.isFinite() }
            ?: return CombinedResult.Invalid("Alla villkor måste ha ett giltigt värde")
        if (c.kind == ConditionKind.DRAWDOWN && v !in 0.0..100.0) return CombinedResult.Invalid("Procent måste vara mellan 0 och 100")
        ruleFor(symbol, c, v)
    }
    var expr: AlertExpression = AlertExpression.Single(rules.first())
    for (i in 1 until rules.size) {
        val next = AlertExpression.Single(rules[i])
        expr = if (conditions[i].or) AlertExpression.Or(expr, next) else AlertExpression.And(expr, next)
    }
    return CombinedResult.Ok(expr)
}

private fun number(v: Double) = if (v == Math.floor(v) && v < 1e9) v.toLong().toString() else v.toString()

/**
 * Motsatsen till [buildCombined]: plattar ut ett sparat uttryck till (symbol, villkor) för redigering.
 * Returnerar `null` när strukturen inte kan visas i byggaren (NOT, flera aktier, SMA, intervall, högerkapslade grupper).
 */
internal fun decomposeCombined(expression: AlertExpression): Pair<String, List<ConditionDraft>>? {
    var symbol: String? = null
    val out = mutableListOf<ConditionDraft>()

    fun leaf(rule: AlertRule, or: Boolean): Boolean {
        val ruleSymbol = when (rule) {
            is AlertRule.SinglePrice -> rule.symbol
            is AlertRule.SingleDrawdownFromHigh -> rule.symbol
            is AlertRule.SingleDailyMove -> rule.symbol
            is AlertRule.SingleKeyMetric -> rule.symbol
            else -> return false
        }
        if (symbol == null) symbol = ruleSymbol else if (symbol != ruleSymbol) return false
        val draft = when (rule) {
            is AlertRule.SinglePrice -> {
                if (rule.comparisonType == AlertRule.PriceComparisonType.WITHIN_RANGE) return false
                ConditionDraft(ConditionKind.PRICE, above = rule.comparisonType == AlertRule.PriceComparisonType.ABOVE, value = number(rule.priceLimit), or = or)
            }
            is AlertRule.SingleDrawdownFromHigh -> {
                if (rule.dropType != AlertRule.DrawdownDropType.PERCENTAGE) return false
                ConditionDraft(ConditionKind.DRAWDOWN, reference = rule.reference, value = number(rule.dropValue), or = or)
            }
            is AlertRule.SingleDailyMove -> ConditionDraft(ConditionKind.DAILY_MOVE, value = number(rule.percentThreshold), or = or)
            is AlertRule.SingleKeyMetric -> {
                if (rule.direction == AlertRule.PriceComparisonType.WITHIN_RANGE) return false
                val kind = when (rule.metricType) {
                    AlertRule.KeyMetricType.PE_RATIO -> ConditionKind.PE
                    AlertRule.KeyMetricType.PS_RATIO -> ConditionKind.PS
                    AlertRule.KeyMetricType.DIVIDEND_YIELD -> ConditionKind.DIVIDEND
                    AlertRule.KeyMetricType.EARNINGS_PER_SHARE -> ConditionKind.EPS
                }
                ConditionDraft(kind, above = rule.direction == AlertRule.PriceComparisonType.ABOVE, value = number(rule.targetValue), or = or)
            }
            else -> return false
        }
        out += draft
        return true
    }

    fun walk(e: AlertExpression, or: Boolean): Boolean = when (e) {
        is AlertExpression.Single -> leaf(e.rule, or)
        is AlertExpression.And -> walk(e.left, or) && e.right is AlertExpression.Single && leaf(e.right.rule, false)
        is AlertExpression.Or -> walk(e.left, or) && e.right is AlertExpression.Single && leaf(e.right.rule, true)
        is AlertExpression.Not -> false
    }

    if (!walk(expression, false)) return null
    val s = symbol ?: return null
    return s to out.toList()
}
