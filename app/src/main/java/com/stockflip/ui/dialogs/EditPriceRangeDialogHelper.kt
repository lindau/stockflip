package com.stockflip.ui.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.stockflip.CurrencyHelper
import com.stockflip.parseDecimal
import com.stockflip.R
import com.stockflip.WatchItem
import com.stockflip.WatchType

/**
 * Visar dialog för att redigera en prisintervall-bevakning (WatchType.PriceRange).
 * Används från MainActivity och StockDetailFragment för att undvika duplicerad kod.
 *
 * @param context Kontext för layout och dialog
 * @param item Bevakningen att redigera (måste vara WatchType.PriceRange)
 * @param onUpdate Callback med nya min/max-pris när användaren bekräftar
 * @param onDelete Callback när användaren trycker "Ta bort"; null döljer knappen
 * @param onDismiss Callback när dialogen stängs; null = ingen
 */
fun showEditPriceRangeDialog(
    context: Context,
    item: WatchItem,
    onUpdate: (minPrice: Double, maxPrice: Double) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    currency: String = "SEK"
) {
    if (item.watchType !is WatchType.PriceRange) return
    val priceRange = item.watchType
    val currencySymbol = CurrencyHelper.getCurrencySymbol(currency)
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_price_range, null)
    val minPriceInput = dialogView.findViewById<TextInputEditText>(R.id.minPriceInput).apply {
        setText(CurrencyHelper.formatDecimal(priceRange.minPrice))
    }
    val maxPriceInput = dialogView.findViewById<TextInputEditText>(R.id.maxPriceInput).apply {
        setText(CurrencyHelper.formatDecimal(priceRange.maxPrice))
    }
    dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.minPriceLayout)?.hint = "Minsta pris ($currencySymbol)"
    dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.maxPriceLayout)?.hint = "Högsta pris ($currencySymbol)"
    val tickerInput = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView?>(R.id.tickerInput)
    val tickerInputLayout = tickerInput?.parent as? com.google.android.material.textfield.TextInputLayout
    tickerInputLayout?.visibility = android.view.View.GONE
    tickerInput?.setText(item.ticker ?: "")
    tickerInput?.isEnabled = false

    val builder = MaterialAlertDialogBuilder(context)
        .setTitle(context.getString(R.string.dialog_edit_price_range_title))
        .setView(dialogView)
        .setPositiveButton(context.getString(R.string.dialog_button_update)) { _, _ ->
            val minPriceStr = minPriceInput.text.toString()
            val maxPriceStr = maxPriceInput.text.toString()
            if (minPriceStr.isNotEmpty() && maxPriceStr.isNotEmpty()) {
                val minPrice = minPriceStr.parseDecimal()
                val maxPrice = maxPriceStr.parseDecimal()
                if (minPrice != null && maxPrice != null && minPrice > 0 && maxPrice > minPrice) {
                    onUpdate(minPrice, maxPrice)
                } else {
                    Toast.makeText(context, context.getString(R.string.dialog_price_range_invalid), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, context.getString(R.string.dialog_fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton(context.getString(R.string.dialog_button_cancel), null)
    if (onDelete != null) {
        builder.setNeutralButton(context.getString(R.string.dialog_button_remove)) { _, _ -> onDelete() }
    }
    val dialog = builder.create()
    if (onDismiss != null) {
        dialog.setOnDismissListener { onDismiss() }
    }
    dialog.show()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    minPriceInput.post { minPriceInput.requestFocus(); minPriceInput.selectAll() }
}
