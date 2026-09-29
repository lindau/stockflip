package com.stockflip.ui.components

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

private val SWEDISH = Locale("sv", "SE")

/** Riktigt minustecken (U+2212) — bredden matchar plus och siffror i Inter. */
const val MINUS = "−"

private fun decimalFormat(decimals: Int) = DecimalFormat(
    if (decimals > 0) "#,##0." + "0".repeat(decimals) else "#,##0",
    DecimalFormatSymbols(SWEDISH)
)

/** Pris/tal på svenskt format, t.ex. `248,30`. Negativa värden får [MINUS]. */
fun formatNumber(value: Double, decimals: Int = 2): String {
    val body = decimalFormat(decimals).format(abs(value))
    return if (value < 0 && body.any { it in '1'..'9' }) MINUS + body else body
}

/** Förändring i procent med alltid synligt tecken, t.ex. `+1,2 %`, `−0,8 %`, `0,0 %`. */
fun formatSignedPercent(value: Double, decimals: Int = 1): String =
    signed(value, decimals) + " %"

/** Förändring i absoluta tal med alltid synligt tecken, t.ex. `+2,95`. */
fun formatSignedAmount(value: Double, decimals: Int = 2): String =
    signed(value, decimals)

private fun signed(value: Double, decimals: Int): String {
    val body = decimalFormat(decimals).format(abs(value))
    val isZero = body.none { it in '1'..'9' }
    return when {
        isZero -> body
        value > 0 -> "+$body"
        else -> MINUS + body
    }
}

/** Var [value] ligger mellan [low] och [high], 0f..1f. Ogiltigt intervall ger 0f. */
fun rangeFraction(value: Double, low: Double, high: Double): Float {
    if (high <= low) return 0f
    return ((value - low) / (high - low)).toFloat().coerceIn(0f, 1f)
}
