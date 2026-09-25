package com.stockflip.ui.dialogs

import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout
import com.stockflip.R
import com.stockflip.parseDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

private fun View.enclosingTextInputLayout(): TextInputLayout? {
    var current = parent
    while (current != null) {
        if (current is TextInputLayout) return current
        current = current.parent
    }
    return null
}

/**
 * Visar felet direkt vid fältet (i stället för en Toast bakom en stängd dialog) och
 * tar bort det när användaren ändrar texten.
 */
internal fun EditText.showFieldError(message: String) {
    val layout = enclosingTextInputLayout()
    if (layout == null) {
        error = message
        requestFocus()
        return
    }
    layout.error = message
    if (getTag(R.id.field_error_watcher) == null) {
        setTag(R.id.field_error_watcher, true)
        doAfterTextChanged { layout.error = null }
    }
    requestFocus()
}

/**
 * Standardlyssnaren för positiv knapp stänger alltid dialogen, även vid ogiltig inmatning.
 * Här ersätts den så att dialogen bara stängs när [onConfirm] returnerar true; annars ligger
 * den kvar med inmatningen orörd. Knappen är inaktiv medan åtgärden pågår (inga dubbelklick).
 * Kräver att positiv knapp satts i buildern och att dialogen visats.
 */
internal fun AlertDialog.setPositiveActionKeepingOpen(scope: CoroutineScope, onConfirm: suspend () -> Boolean) {
    val button = getButton(AlertDialog.BUTTON_POSITIVE) ?: return
    button.setOnClickListener {
        button.isEnabled = false
        scope.launch {
            try {
                if (onConfirm()) dismiss()
            } finally {
                button.isEnabled = true
            }
        }
    }
}
