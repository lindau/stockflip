package com.stockflip.ui.watchlist

import com.stockflip.WatchItemUiState
import com.stockflip.CurrencyHelper
import com.stockflip.WatchType
import com.stockflip.ui.components.cards.updateFailedLabel
import com.stockflip.ui.components.listText
import com.stockflip.ui.components.triggerWhen
import com.stockflip.ui.createwatch.describeExpression
import com.stockflip.isTriggeredForDisplay
import com.stockflip.triggerProximity
import com.stockflip.ui.components.MINUS
import com.stockflip.ui.components.formatNumber
import com.stockflip.ui.components.formatSignedPercent
import kotlin.math.abs
import kotlin.math.floor

/** Allt en rad i den platta bevakningslistan behöver — färdigformaterat. */
internal data class WatchRowModel(
    val id: Int,
    val title: String,
    val subtitle: String,
    val price: String,
    val priceValue: Double,
    val change: String?,
    /** `true` = uppgång, `false` = nedgång, `null` = oförändrat/okänt. */
    val changePositive: Boolean?,
    val triggered: Boolean,
    val paused: Boolean,
    val isPair: Boolean,
    /** Symbol att öppna detaljsidan för; `null` för par och bevakningar utan enskild aktie. */
    val symbol: String?,
    /** "Kunde inte uppdateras · visar värden från 14:32" när senaste kursuppdateringen misslyckades, annars null. */
    val staleLabel: String? = null,
    /** Aktien har en anteckning. */
    val hasNote: Boolean = false,
    /** Bolaget har nämnts i ett poddavsnitt. */
    val hasPodcast: Boolean = false,
    /** Bara villkoret ("Över 245 kr"), utan status. [subtitle] = villkor + [statusSuffix]. */
    val condition: String = subtitle,
    /** Status efter villkoret, t.ex. "utlöst 09:14" eller "utlöst igår · pausad"; `null` när inget att visa. */
    val statusSuffix: String? = null,
    /** Dagsförändring i procent för sortering; `null` för par och okänt. */
    val changeValue: Double? = null,
    /** Hur nära villkoret är att utlösas: 0.0 = utlöst nu, 1.0 = långt kvar; `null` när det inte kan beräknas eller bevakningen är pausad. */
    val proximity: Double? = null,
    /** Kan återaktiveras manuellt; `false` för insideraffärer som alltid är igång. */
    val reactivatable: Boolean = true,
)

/** Vad listan sorteras på. [defaultDescending] = riktningen när sorteringen väljs första gången. */
internal enum class WatchSortKey(val defaultDescending: Boolean) {
    CREATED(false),
    NAME(false),
    /** Dagsutveckling; fallande = störst uppgång först. */
    CHANGE(true),
    /** Närmast aktivering; stigande = närmast först. */
    PROXIMITY(false),
}

/** Sorteringsordning för bevakningslistan; gäller inom varje sektion (Utlösta / Väntar). */
internal data class WatchSort(val key: WatchSortKey = WatchSortKey.CREATED, val descending: Boolean = false) {
    /** Standardsorteringen (skapad, äldst först) — då visas sorteringsikonen neutral. */
    val isDefault: Boolean get() = key == WatchSortKey.CREATED && !descending

    /** Tryck på [tapped]: samma sortering vänder riktningen, en annan väljs med sin standardriktning. */
    fun tapped(tapped: WatchSortKey): WatchSort =
        if (tapped == key) copy(descending = !descending) else WatchSort(tapped, tapped.defaultDescending)

    companion object {
        /** Läser sparat val; förstår även de gamla enumnamnen. Okänt värde ger standardsorteringen. */
        fun fromPrefs(name: String?, descending: Boolean): WatchSort = when (name) {
            "CREATED", "NAME", "CHANGE", "PROXIMITY" -> WatchSort(WatchSortKey.valueOf(name), descending)
            "CHANGE_DESC" -> WatchSort(WatchSortKey.CHANGE, true)
            "CHANGE_ASC" -> WatchSort(WatchSortKey.CHANGE, false)
            "NEAREST" -> WatchSort(WatchSortKey.PROXIMITY, false)
            else -> WatchSort()
        }
    }
}

private fun List<WatchRowModel>.sortedFor(sort: WatchSort): List<WatchRowModel> {
    // Rader utan värde (okänd förändring/närhet, pausade, par) ligger sist oavsett riktning.
    fun <T : Comparable<T>> List<WatchRowModel>.byNullable(value: (WatchRowModel) -> T?): List<WatchRowModel> {
        val (known, unknown) = partition { value(it) != null }
        val sorted = if (sort.descending) known.sortedByDescending { value(it) } else known.sortedBy { value(it) }
        return sorted + unknown
    }
    return when (sort.key) {
        WatchSortKey.CREATED -> if (sort.descending) reversed() else this
        WatchSortKey.NAME -> sortedWith(
            if (sort.descending) compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }
            else compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        WatchSortKey.CHANGE -> byNullable { it.changeValue }
        WatchSortKey.PROXIMITY -> byNullable { it.proximity }
    }
}

internal data class WatchListSections(
    val triggered: List<WatchRowModel>,
    val waiting: List<WatchRowModel>,
) {
    val isEmpty: Boolean get() = triggered.isEmpty() && waiting.isEmpty()
}

private const val DASH = "–"

internal fun WatchItemUiState.toRowModel(
    triggerMillis: Long? = null,
    now: Long = System.currentTimeMillis(),
    notedTickers: Set<String> = emptySet(),
    mentionedTickers: Set<String> = emptySet(),
): WatchRowModel {
    val wt = item.watchType
    val isPair = wt is WatchType.PricePair
    val triggered = isTriggeredForDisplay()

    val title = when {
        isPair -> "${item.companyName1 ?: item.ticker1 ?: ""} $DASH ${item.companyName2 ?: item.ticker2 ?: ""}"
        else -> item.companyName ?: item.ticker ?: item.getDisplayName()
    }

    val priceValue = if (isPair) abs(live.currentPrice1 - live.currentPrice2) else live.currentPrice
    val hasPrice = if (isPair) live.currentPrice1 > 0 && live.currentPrice2 > 0 else live.currentPrice > 0
    val dailyChange = if (isPair) null else live.currentDailyChangePercent

    val conditionOnly = conditionText(triggered)
    val statusParts = buildList {
        if (triggered) add(triggerWhen(triggerMillis, item.lastTriggeredDate, now)?.listText() ?: "utlöst")
        if (!item.isActive) add("pausad")
    }
    val statusSuffix = statusParts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    val subtitle = if (statusSuffix == null) conditionOnly else "$conditionOnly · $statusSuffix"

    return WatchRowModel(
        id = item.id,
        title = title,
        subtitle = subtitle,
        price = if (hasPrice) formatNumber(priceValue) else DASH,
        priceValue = if (hasPrice) priceValue else 0.0,
        change = dailyChange?.let { formatSignedPercent(it) },
        changePositive = when {
            dailyChange == null -> null
            formatSignedPercent(dailyChange).startsWith("+") -> true
            formatSignedPercent(dailyChange).startsWith(MINUS) -> false
            else -> null
        },
        triggered = triggered,
        reactivatable = item.watchType !is WatchType.InsiderBuy,
        paused = !item.isActive,
        isPair = isPair,
        symbol = if (isPair) null else item.ticker,
        staleLabel = updateFailedLabel(live),
        // Par har ingen enskild aktie och får därför inga ikoner. Podd-tickers jämförs i versaler (som i MainViewModel).
        hasNote = !isPair && item.ticker != null && item.ticker in notedTickers,
        hasPodcast = !isPair && item.ticker != null && item.ticker.uppercase() in mentionedTickers,
        condition = conditionOnly,
        statusSuffix = statusSuffix,
        changeValue = dailyChange,
        proximity = if (item.isActive) triggerProximity() else null,
    )
}

private fun WatchItemUiState.conditionText(triggered: Boolean): String = conditionText(triggered, currencyUnit(item.ticker ?: item.ticker1))

private fun WatchItemUiState.conditionText(triggered: Boolean, unit: String): String = when (val wt = item.watchType) {
    is WatchType.PriceTarget -> {
        val above = wt.direction == WatchType.PriceDirection.ABOVE
        val base = "${if (above) "Över" else "Under"} ${number(wt.targetPrice)} $unit"
        if (!triggered && live.currentPrice > 0) {
            val remaining = abs(wt.targetPrice - live.currentPrice) / live.currentPrice * 100
            "$base · ${formatNumber(remaining, 1)} % kvar"
        } else base
    }
    is WatchType.ATHBased -> {
        val ref = if (wt.reference == WatchType.HighReference.ALL_TIME_HIGH) "all-time-high" else "52v-högsta"
        val amount = when (wt.dropType) {
            WatchType.DropType.PERCENTAGE -> "$MINUS${number(wt.dropValue)} %"
            WatchType.DropType.ABSOLUTE -> "$MINUS${number(wt.dropValue)} $unit"
        }
        val now = when (wt.dropType) {
            WatchType.DropType.PERCENTAGE -> live.currentDropPercentage.takeIf { it > 0 }
                ?.let { " · nu $MINUS${formatNumber(it, 1)} %" }
            WatchType.DropType.ABSOLUTE -> live.currentDropAbsolute.takeIf { it > 0 }
                ?.let { " · nu $MINUS${formatNumber(it)} $unit" }
        }.orEmpty()
        "Från $ref $amount" + if (triggered) "" else now
    }
    is WatchType.DailyMove -> when (wt.direction) {
        WatchType.DailyMoveDirection.UP -> "Dagsrörelse över +${number(wt.percentThreshold)} %"
        WatchType.DailyMoveDirection.DOWN -> "Dagsrörelse under $MINUS${number(wt.percentThreshold)} %"
        WatchType.DailyMoveDirection.BOTH -> "Dagsrörelse ±${number(wt.percentThreshold)} %"
    }
    is WatchType.KeyMetrics -> {
        val name = when (wt.metricType) {
            WatchType.MetricType.PE_RATIO -> "P/E"
            WatchType.MetricType.PS_RATIO -> "P/S"
            WatchType.MetricType.DIVIDEND_YIELD -> "Direktavkastning"
            WatchType.MetricType.EARNINGS_PER_SHARE -> "Vinst per aktie"
        }
        val dir = if (wt.direction == WatchType.PriceDirection.ABOVE) "över" else "under"
        val now = live.currentMetricValue.takeIf { it > 0 && !triggered }
            ?.let { " · nu ${formatNumber(it, 1)}" }.orEmpty()
        "$name $dir ${number(wt.targetValue)}$now"
    }
    is WatchType.PriceRange -> "Mellan ${number(wt.minPrice)} och ${number(wt.maxPrice)} $unit"
    is WatchType.PricePair -> "Prisskillnad ${number(wt.priceDifference)} $unit"
    is WatchType.PriceVsSma -> {
        val dir = if (wt.direction == WatchType.PriceDirection.ABOVE) "över" else "under"
        "Pris $dir SMA ${wt.period}"
    }
    is WatchType.SmaCrossover -> {
        val dir = if (wt.direction == WatchType.PriceDirection.ABOVE) "över" else "under"
        "SMA ${wt.shortPeriod} $dir SMA ${wt.longPeriod}"
    }
    is WatchType.InsiderBuy -> "Insiderköp"
    is WatchType.Combined -> describeExpression(wt.expression)
}

/** Heltal utan decimaler, annars två decimaler. */
private fun number(v: Double): String = formatNumber(v, if (v == floor(v)) 0 else 2)

/**
 * Delar upp raderna i "Utlösta" (överst) och "Väntar". [query] filtrerar på titel och symbol.
 * Ordningen inom varje grupp följer [sort] (standard: som den kom in).
 */
internal fun sectionsFor(
    items: List<WatchItemUiState>,
    query: String = "",
    triggerTimes: Map<Int, Long> = emptyMap(),
    now: Long = System.currentTimeMillis(),
    notedTickers: Set<String> = emptySet(),
    mentionedTickers: Set<String> = emptySet(),
    sort: WatchSort = WatchSort(),
): WatchListSections {
    val q = query.trim()
    val rows = items.map { it.toRowModel(triggerTimes[it.item.id], now, notedTickers, mentionedTickers) }
        .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.symbol?.contains(q, ignoreCase = true) == true }
    return WatchListSections(
        triggered = rows.filter { it.triggered }.sortedFor(sort),
        waiting = rows.filterNot { it.triggered }.sortedFor(sort),
    )
}

/** Vilken vy bevakningssidan visar: bevakningar (Utlösta/Väntar) eller en rad per aktie. */
internal enum class WatchView { WATCHES, STOCKS }

internal data class StockListSections(
    val stocks: List<WatchRowModel>,
    val pairs: List<WatchRowModel>,
) {
    val isEmpty: Boolean get() = stocks.isEmpty() && pairs.isEmpty()
}

/**
 * Aktievyn: en rad per aktie som har minst en bevakning (aktier utan bevakning finns inte), plus aktiepar
 * som egna rader. Aktier med utlösta bevakningar ligger överst; därefter följer ordningen [sort].
 * Kurs, förändring och ikoner hämtas från aktiens första bevakning med giltig kurs.
 */
internal fun stockSectionsFor(
    items: List<WatchItemUiState>,
    query: String = "",
    triggerTimes: Map<Int, Long> = emptyMap(),
    now: Long = System.currentTimeMillis(),
    notedTickers: Set<String> = emptySet(),
    mentionedTickers: Set<String> = emptySet(),
    sort: WatchSort = WatchSort(),
): StockListSections {
    val q = query.trim()
    fun matches(row: WatchRowModel) =
        q.isEmpty() || row.title.contains(q, ignoreCase = true) || row.symbol?.contains(q, ignoreCase = true) == true

    val (pairItems, stockItems) = items.partition { it.item.watchType is WatchType.PricePair }

    val stocks = stockItems.filter { it.item.ticker != null }.groupBy { it.item.ticker!! }.map { (_, group) ->
        val rows = group.map { it.toRowModel(triggerTimes[it.item.id], now, notedTickers, mentionedTickers) }
        val base = rows.firstOrNull { it.priceValue > 0 } ?: rows.first()
        val triggeredCount = rows.count { it.triggered }
        val count = rows.size
        val countText = if (count == 1) "1 bevakning" else "$count bevakningar"
        base.copy(
            subtitle = listOfNotNull(countText, triggeredCount.takeIf { it > 0 }?.let { "$it utlöst" }).joinToString(" · "),
            condition = countText,
            statusSuffix = null,
            triggered = triggeredCount > 0,
            paused = rows.all { it.paused },
            staleLabel = base.staleLabel,
            proximity = rows.mapNotNull { it.proximity }.minOrNull(),
        )
    }.filter(::matches)

    val pairs = pairItems.map { it.toRowModel(triggerTimes[it.item.id], now, notedTickers, mentionedTickers) }.filter(::matches)

    return StockListSections(
        stocks = stocks.filter { it.triggered }.sortedFor(sort) + stocks.filterNot { it.triggered }.sortedFor(sort),
        pairs = pairs.sortedFor(sort),
    )
}

/** "Uppdaterad 14:32" utifrån den senaste lyckade kursuppdateringen i listan, eller null om ingen hämtats. */
internal fun lastUpdatedLabel(items: List<WatchItemUiState>, format: (Long) -> String): String? {
    val latest = items.filter { !it.live.updateFailed }.maxOfOrNull { it.live.lastUpdatedAt } ?: 0L
    return if (latest > 0L) "Uppdaterad ${format(latest)}" else null
}

/** Valutaenhet för en ticker, t.ex. "kr", "$" eller "€"; "kr" när valutan är okänd. */
internal fun currencyUnit(ticker: String?): String = CurrencyHelper.getCurrencySymbol(CurrencyHelper.getCurrencyFromSymbol(ticker))
