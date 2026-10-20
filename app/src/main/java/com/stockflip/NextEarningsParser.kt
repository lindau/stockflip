package com.stockflip

import org.json.JSONObject

/**
 * Tolkar `result[0]` i ett Yahoo quoteSummary-svar (moduler calendarEvents och earnings) till
 * [NextEarningsInfo]. calendarEvents har oftast datum för fler bolag än earningsChart, så den
 * prövas först. Returnerar null om inget kommande datum finns.
 */
internal fun parseNextEarnings(result: JSONObject?, nowMillis: Long): NextEarningsInfo? {
    result ?: return null
    val chart = result.optJSONObject("earnings")?.optJSONObject("earningsChart")

    // Marginal bakåt så att en rapport som släpps idag (tidigare på dagen) fortfarande räknas.
    val earliest = nowMillis - 12L * 60L * 60L * 1000L
    fun firstUpcoming(dates: org.json.JSONArray?): Long? {
        dates ?: return null
        return (0 until dates.length())
            .mapNotNull { dates.optJSONObject(it)?.optLong("raw")?.takeIf { raw -> raw > 0L }?.times(1000L) }
            .filter { it >= earliest }
            .minOrNull()
    }

    val dateMillis = firstUpcoming(result.optJSONObject("calendarEvents")?.optJSONObject("earnings")?.optJSONArray("earningsDate"))
        ?: firstUpcoming(chart?.optJSONArray("earningsDate"))
        ?: return null

    val isAnnualReport = chart?.optString("currentQuarterEstimate", null)?.equals("4q", ignoreCase = true) ?: false
    return NextEarningsInfo(reportDateMillis = dateMillis, isAnnualReport = isAnnualReport)
}
