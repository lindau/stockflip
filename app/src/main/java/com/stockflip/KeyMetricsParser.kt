package com.stockflip

import org.json.JSONObject
import kotlin.math.abs

/**
 * Tolkar `result[0]` i ett Yahoo quoteSummary-svar (moduler summaryDetail, defaultKeyStatistics
 * och financialData) till [KeyMetrics]. Returnerar null om ingen av modulerna finns.
 */
internal fun parseKeyMetrics(result: JSONObject?): KeyMetrics? {
    val summaryDetail = result?.optJSONObject("summaryDetail")
    val defaultKeyStatistics = result?.optJSONObject("defaultKeyStatistics")
    val financialData = result?.optJSONObject("financialData")
    if (summaryDetail == null && defaultKeyStatistics == null && financialData == null) return null

    val pe = summaryDetail?.optJSONObject("trailingPE")?.optDouble("raw").takeIf { it != null && !it.isNaN() && it > 0 }
        ?: summaryDetail?.optJSONObject("forwardPE")?.optDouble("raw").takeIf { it != null && !it.isNaN() && it > 0 }
    val ps = summaryDetail?.optJSONObject("priceToSalesTrailing12Months")?.optDouble("raw").takeIf { it != null && !it.isNaN() && it > 0 }
    // Prova dividendYield först, sedan trailingAnnualDividendYield (vanligt för icke-amerikanska aktier)
    val yieldRaw = summaryDetail?.optJSONObject("dividendYield")?.optDouble("raw")
        ?.takeIf { !it.isNaN() && it > 0 }
        ?: summaryDetail?.optJSONObject("trailingAnnualDividendYield")?.optDouble("raw")
            ?.takeIf { !it.isNaN() && it > 0 }
    val dividendYield = yieldRaw?.let { it * 100 }
    val marketCap = summaryDetail?.optJSONObject("marketCap")?.optDouble("raw")
        ?.takeIf { !it.isNaN() && it > 0 }
    val returnOnEquityRaw = financialData?.optJSONObject("returnOnEquity")?.optDouble("raw")
        ?.takeIf { !it.isNaN() }
    val returnOnEquity = returnOnEquityRaw?.let { if (abs(it) <= 1.0) it * 100 else it }
    val earningsPerShare = defaultKeyStatistics?.optJSONObject("trailingEps")?.optDouble("raw")
        ?.takeIf { !it.isNaN() && it > 0 }
        ?: defaultKeyStatistics?.optJSONObject("forwardEps")?.optDouble("raw")
            ?.takeIf { !it.isNaN() && it > 0 }

    // Negativa värden är meningsfulla här (förlust => negativt EV/EBITDA, negativt eget kapital =>
    // negativt P/B och skuldsättningsgrad), så bara NaN/saknat filtreras bort.
    val priceToBook = defaultKeyStatistics?.rawOrNull("priceToBook")
    val evToEbitda = defaultKeyStatistics?.rawOrNull("enterpriseToEbitda")
    // Yahoos debtToEquity anges redan i procent (45,2 = 45,2 %) — ingen omräkning.
    val debtToEquity = financialData?.rawOrNull("debtToEquity")

    val targetMeanPrice = financialData?.rawOrNull("targetMeanPrice")?.takeIf { it > 0 }
    val targetHighPrice = financialData?.rawOrNull("targetHighPrice")?.takeIf { it > 0 }
    val targetLowPrice = financialData?.rawOrNull("targetLowPrice")?.takeIf { it > 0 }
    val analystCount = financialData?.rawOrNull("numberOfAnalystOpinions")?.toInt()?.takeIf { it > 0 }
    // Yahoo skickar "none" när rekommendation saknas.
    val recommendationKey = financialData?.optString("recommendationKey")
        ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "none" && it != "null" }
    val financialCurrency = financialData?.optString("financialCurrency")
        ?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    return KeyMetrics(
        peRatio = pe,
        psRatio = ps,
        dividendYield = dividendYield,
        earningsPerShare = earningsPerShare,
        marketCap = marketCap,
        returnOnEquity = returnOnEquity,
        priceToBook = priceToBook,
        evToEbitda = evToEbitda,
        debtToEquity = debtToEquity,
        targetMeanPrice = targetMeanPrice,
        targetHighPrice = targetHighPrice,
        targetLowPrice = targetLowPrice,
        analystCount = analystCount,
        recommendationKey = recommendationKey,
        financialCurrency = financialCurrency
    )
}

/** `{"key": {"raw": 1.23}}` -> 1.23, eller null om nyckeln/`raw` saknas eller är NaN. */
private fun JSONObject.rawOrNull(key: String): Double? {
    val raw = optJSONObject(key) ?: return null
    if (!raw.has("raw")) return null
    return raw.optDouble("raw").takeIf { !it.isNaN() }
}
