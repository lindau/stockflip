package com.stockflip

import kotlin.math.abs

data class WatchReactivationResult(
    val watchItem: WatchItem,
    val sameDayTriggerGuarded: Boolean
)

fun WatchItem.hasPendingNextTradingDayGuard(
    today: String = WatchItem.getTodayDateString()
): Boolean {
    if (watchType is WatchType.InsiderBuy) return false
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
        "$base. Utvärderas vid nästa kursuppdatering."
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

fun WatchType.PriceDirection.swedishDirectionLabel(): String {
    return when (this) {
        WatchType.PriceDirection.ABOVE -> "över"
        WatchType.PriceDirection.BELOW -> "under"
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
