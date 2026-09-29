package com.stockflip.ui.dialogs

import com.stockflip.parseDecimal

/** Resultat av att tolka ett fält som ett positivt decimaltal. */
internal sealed class DecimalInput {
    data class Valid(val value: Double) : DecimalInput()
    data class Invalid(val message: String) : DecimalInput()
}

/** Ren validering (enhetstestbar): tomt fält och ogiltigt/icke-positivt värde ger olika meddelanden. */
internal fun validatePositiveDecimal(raw: String?, emptyMessage: String, invalidMessage: String): DecimalInput {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return DecimalInput.Invalid(emptyMessage)
    val value = text.parseDecimal()
    return if (value != null && value.isFinite() && value > 0.0) DecimalInput.Valid(value)
    else DecimalInput.Invalid(invalidMessage)
}

/**
 * Spread för aktiepar: tomt fält betyder 0 (som tidigare), men text som inte är ett tal
 * ska ge null (fel vid fältet) i stället för att tyst bli 0.
 */
internal fun parsePairSpread(raw: String?): Double? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return 0.0
    return text.parseDecimal()?.takeIf { it.isFinite() }
}

internal const val SAVE_FAILED_MESSAGE: String = "Kunde inte spara bevakningen. Försök igen."
internal const val DUPLICATE_WATCH_MESSAGE: String = "En bevakning med dessa inställningar finns redan"
