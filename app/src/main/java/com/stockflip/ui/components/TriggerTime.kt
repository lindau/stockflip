package com.stockflip.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class TriggerDay { Today, Yesterday, Older }

/** När en bevakning senast utlöstes, uppdelat i dag/igår/äldre. [time] saknas när bara ett datum finns. */
data class TriggerWhen(val day: TriggerDay, val time: String?, val date: String)

private val SV = Locale("sv", "SE")

private fun startOfDay(millis: Long, zone: TimeZone): Long =
    Calendar.getInstance(zone).apply { timeInMillis = millis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

/**
 * Tolkar utlösningstiden. [triggeredAtMillis] (från trigger-historiken) ger klockslag; annars används
 * [lastTriggeredDate] ("YYYY-MM-DD") utan klockslag. Null om ingen av dem finns.
 */
fun triggerWhen(triggeredAtMillis: Long?, lastTriggeredDate: String?, now: Long, zone: TimeZone = TimeZone.getDefault()): TriggerWhen? {
    val (millis, hasTime) = when {
        triggeredAtMillis != null && triggeredAtMillis > 0L -> triggeredAtMillis to true
        !lastTriggeredDate.isNullOrBlank() -> {
            val parsed = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.parse(lastTriggeredDate)?.time } catch (e: Exception) { null }
            (parsed ?: return null) to false
        }
        else -> return null
    }
    val days = ((startOfDay(now, zone) - startOfDay(millis, zone)) / 86_400_000.0).roundToInt()
    val day = when {
        days <= 0 -> TriggerDay.Today
        days == 1 -> TriggerDay.Yesterday
        else -> TriggerDay.Older
    }
    val time = if (hasTime) SimpleDateFormat("HH:mm", SV).apply { timeZone = zone }.format(Date(millis)) else null
    // Vissa plattformar ger "sep." med punkt; designen använder "12 sep".
    val date = SimpleDateFormat("d MMM", SV).apply { timeZone = zone }.format(Date(millis)).replace(".", "")
    return TriggerWhen(day, time, date)
}

/** Kort text för listan: "utlöst 09:14", "utlöst idag", "utlöst igår", "utlöst 12 sep". */
fun TriggerWhen.listText(): String = "utlöst " + when (day) {
    TriggerDay.Today -> time ?: "idag"
    TriggerDay.Yesterday -> "igår"
    TriggerDay.Older -> date
}

/** Text för aktiedetaljen: "Utlöst idag 09:14", "Utlöst igår 16:02", "Utlöst 12 sep". */
fun TriggerWhen.detailText(): String = "Utlöst " + when (day) {
    TriggerDay.Today -> listOfNotNull("idag", time).joinToString(" ")
    TriggerDay.Yesterday -> listOfNotNull("igår", time).joinToString(" ")
    TriggerDay.Older -> date
}
