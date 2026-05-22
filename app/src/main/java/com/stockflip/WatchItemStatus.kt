package com.stockflip

import kotlin.math.abs

data class WatchReactivationResult(
    val watchItem: WatchItem,
    val sameDayTriggerGuarded: Boolean
)

fun WatchItem.hasPendingNextTradingDayGuard(
    today: String = WatchItem.getTodayDateString()
): Boolean {
    return isActive && !isTriggered && lastTriggeredDate == today
}

fun WatchReactivationResult.toUserMessage(currency: String? = null): String {
    val base = when (watchItem.watchType) {
        is WatchType.PriceTarget -> {
            "Bevakning återaktiverad: ${watchItem.armedConditionDescription(currency)}"
        }
        is WatchType.PricePair -> {
            "Bevakning återaktiverad: ${watchItem.armedConditionDescription(currency)}"
        }
        else -> "Bevakning återaktiverad"
    }
    return if (sameDayTriggerGuarded) {
        "$base. Kan trigga först nästa handelsdag."
    } else {
        base
    }
}

fun WatchItem.armedConditionDescription(currency: String? = null): String {
    return when (val type = watchType) {
        is WatchType.PriceTarget -> {
            val direction = type.direction.swedishDirectionLabel()
            val target = CurrencyHelper.formatPrice(type.targetPrice, currency ?: CurrencyHelper.getCurrencyFromSymbol(ticker))
            "bevakar kurs $direction $target"
        }
        is WatchType.PricePair -> {
            val target = CurrencyHelper.formatDecimal(type.priceDifference)
            val equalText = if (type.notifyWhenEqual) " eller lika pris" else ""
            "bevakar spread >= $target oavsett riktning$equalText"
        }
        else -> getWatchTypeDisplayName()
    }
}

fun WatchItem.triggerConditionText(
    currency: String? = null,
    decimalFormat: (Double) -> String = { CurrencyHelper.formatDecimal(it) }
): String {
    return when (val type = watchType) {
        is WatchType.PriceTarget -> {
            val operator = type.direction.comparisonOperator()
            val target = CurrencyHelper.formatPrice(type.targetPrice, currency ?: CurrencyHelper.getCurrencyFromSymbol(ticker))
            "Kurs $operator $target"
        }
        is WatchType.PricePair -> {
            val spreadText = type.priceDifference.takeIf { it > 0.0 }?.let {
                "Spread >= ${decimalFormat(it)}"
            }
            val equalText = if (type.notifyWhenEqual) "Lika pris" else null
            listOfNotNull(spreadText, equalText).joinToString(" eller ").ifBlank { "Lika pris" }
        }
        is WatchType.KeyMetrics -> {
            val metric = type.metricType.shortLabel()
            val operator = type.direction.comparisonOperator()
            "$metric $operator ${type.metricTargetText(decimalFormat)}"
        }
        is WatchType.ATHBased -> {
            val target = when (type.dropType) {
                WatchType.DropType.PERCENTAGE -> "${decimalFormat(type.dropValue)}%"
                WatchType.DropType.ABSOLUTE -> {
                    val code = currency ?: CurrencyHelper.getCurrencyFromSymbol(ticker)
                    CurrencyHelper.formatPrice(type.dropValue, code)
                }
            }
            "Drawdown >= $target från ${type.reference.shortLabel()}"
        }
        is WatchType.DailyMove -> {
            val target = "${decimalFormat(type.percentThreshold)}%"
            when (type.direction) {
                WatchType.DailyMoveDirection.UP -> "Dagsrörelse >= $target"
                WatchType.DailyMoveDirection.DOWN -> "Dagsrörelse <= -$target"
                WatchType.DailyMoveDirection.BOTH -> "|Dagsrörelse| >= $target"
            }
        }
        is WatchType.PriceRange -> {
            val code = currency ?: CurrencyHelper.getCurrencyFromSymbol(ticker)
            "Kurs mellan ${CurrencyHelper.formatPrice(type.minPrice, code)} och ${CurrencyHelper.formatPrice(type.maxPrice, code)}"
        }
        is WatchType.InsiderBuy -> "Nytt insiderköp"
        is WatchType.Combined -> type.expression.getDescription()
    }
}

fun WatchType.PriceDirection.swedishDirectionLabel(): String {
    return when (this) {
        WatchType.PriceDirection.ABOVE -> "över"
        WatchType.PriceDirection.BELOW -> "under"
    }
}

private fun WatchType.PriceDirection.comparisonOperator(): String {
    return when (this) {
        WatchType.PriceDirection.ABOVE -> ">="
        WatchType.PriceDirection.BELOW -> "<="
    }
}

private fun WatchType.MetricType.shortLabel(): String {
    return when (this) {
        WatchType.MetricType.PE_RATIO -> "P/E"
        WatchType.MetricType.PS_RATIO -> "P/S"
        WatchType.MetricType.DIVIDEND_YIELD -> "Direktavkastning"
        WatchType.MetricType.EARNINGS_PER_SHARE -> "Vinst/aktie"
    }
}

private fun WatchType.KeyMetrics.metricTargetText(decimalFormat: (Double) -> String): String {
    return when (metricType) {
        WatchType.MetricType.DIVIDEND_YIELD -> "${decimalFormat(targetValue)}%"
        else -> decimalFormat(targetValue)
    }
}

private fun WatchType.HighReference.shortLabel(): String {
    return when (this) {
        WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> "52v-topp"
        WatchType.HighReference.ALL_TIME_HIGH -> "högsta pris"
    }
}

fun pairSpreadDirectionLabel(
    priceA: Double?,
    priceB: Double?,
    labelA: String?,
    labelB: String?
): String {
    if (priceA == null || priceB == null || priceA <= 0.0 || priceB <= 0.0) return "Spread nu"
    val left = labelA?.takeIf { it.isNotBlank() } ?: "A"
    val right = labelB?.takeIf { it.isNotBlank() } ?: "B"
    return when {
        abs(priceA - priceB) < PairTriggerEvaluator.PRICE_EQUALITY_THRESHOLD -> "Lika pris"
        priceA > priceB -> "$left över $right"
        else -> "$right över $left"
    }
}
