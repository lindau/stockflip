package com.stockflip.ui.dialogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.stockflip.AlertExpression
import com.stockflip.AlertRule
import com.stockflip.CurrencyHelper
import com.stockflip.MainActivity
import com.stockflip.R
import com.stockflip.StockDetailData
import com.stockflip.StockDetailViewModel
import com.stockflip.StockSearchResult
import com.stockflip.UiState
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.parseDecimal
import com.stockflip.repository.SearchState
import com.stockflip.ui.builders.ConditionBuilderAdapter
import com.stockflip.viewmodel.StockSearchViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hanterar alla dialoger för att skapa och redigera bevakningar i StockDetailFragment.
 * Extraherat från StockDetailFragment för att hålla Fragment-klassen som en ren koordinator.
 */
class WatchDialogManager(
    private val fragment: Fragment,
    private val viewModel: StockDetailViewModel,
    private val stockSearchViewModel: StockSearchViewModel,
    private val stockSearchViewModel2: StockSearchViewModel,
    private val symbol: String,
    private val companyName: String?
) {
    private val context get() = fragment.requireContext()
    private val lifecycleScope get() = fragment.viewLifecycleOwner.lifecycleScope

    private fun hideQuickActions() {
        fragment.view?.findViewById<View>(R.id.quickActionsCard)?.visibility = View.GONE
    }

    private fun showQuickActions() {
        fragment.view?.findViewById<View>(R.id.quickActionsCard)?.visibility = View.VISIBLE
    }

    private fun currentCurrencySymbol(): String = CurrencyHelper.getCurrencySymbol(currentCurrency())

    private fun currentCurrency(): String =
        (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.currency ?: "SEK"

    private fun currentCompanyName(): String =
        (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
            ?: companyName ?: ""

    // -------------------------------------------------------------------------
    // Create-dialoger
    // -------------------------------------------------------------------------

    fun showCreatePriceTargetDialog() {
        val currencySymbol = currentCurrencySymbol()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput)

        tickerInput?.parent?.let { parent ->
            if (parent is TextInputLayout) {
                parent.visibility = View.GONE
            } else if (parent is ViewGroup) {
                parent.visibility = View.GONE
            }
        }
        dialogView.findViewById<TextInputLayout>(R.id.targetPriceLayout)?.hint = "Målpris ($currencySymbol)"

        MaterialAlertDialogBuilder(context)
            .setTitle("Skapa målpris-bevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa") { _, _ ->
                val targetPriceStr = targetPriceInput.text.toString()
                if (targetPriceStr.isNotEmpty()) {
                    val targetPrice = targetPriceStr.parseDecimal()
                    if (targetPrice != null && targetPrice > 0) {
                        val currentPrice = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)
                            ?.data?.lastPrice ?: 0.0
                        val direction = if (currentPrice > 0.0 && currentPrice >= targetPrice)
                            WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
                        viewModel.createAlert(WatchType.PriceTarget(targetPrice, direction), currentCompanyName())
                        Toast.makeText(context, "Målpris-bevakning skapad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Ange ett giltigt målpris", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Ange ett målpris", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                targetPriceInput.requestFocus()
            }
    }

    fun showCreateDrawdownDialog() {
        val currencySymbol = currentCurrencySymbol()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput)
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput)

        tickerInput?.parent?.let { parent ->
            if (parent is TextInputLayout) {
                parent.visibility = View.GONE
            } else if (parent is ViewGroup) {
                parent.visibility = View.GONE
            }
        }

        val dropTypes = arrayOf("Procent", "Absolut ($currencySymbol)")
        val dropTypeAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, dropTypes)
        dropTypeInput.setAdapter(dropTypeAdapter)
        dropTypeInput.setText("Procent", false)

        MaterialAlertDialogBuilder(context)
            .setTitle("Skapa drawdown-bevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa") { _, _ ->
                val dropTypeStr = dropTypeInput.text.toString()
                val dropValueStr = dropValueInput.text.toString()
                if (dropTypeStr.isNotEmpty() && dropValueStr.isNotEmpty()) {
                    val dropType = when (dropTypeStr) {
                        "Procent" -> WatchType.DropType.PERCENTAGE
                        else -> WatchType.DropType.ABSOLUTE
                    }
                    val dropValue = dropValueStr.parseDecimal()
                    if (dropValue != null && dropValue > 0) {
                        viewModel.createAlert(WatchType.ATHBased(dropType, dropValue), currentCompanyName())
                        Toast.makeText(context, "Drawdown-bevakning skapad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Ange ett giltigt värde", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                dropValueInput.requestFocus()
            }
    }

    fun showCreateDailyMoveDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_daily_move, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput?.parent as? TextInputLayout
        tickerInputLayout?.visibility = View.GONE
        tickerInput?.setText("$symbol - ${currentCompanyName()}")
        tickerInput?.isEnabled = false

        val thresholdInput = dialogView.findViewById<TextInputEditText>(R.id.thresholdInput)
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput)

        val directions = arrayOf("Upp", "Ned", "Båda")
        val directionAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)

        MaterialAlertDialogBuilder(context)
            .setTitle("Skapa dagsrörelse-bevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa") { _, _ ->
                val thresholdStr = thresholdInput.text.toString()
                val directionStr = directionInput.text.toString()
                if (thresholdStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val threshold = thresholdStr.parseDecimal()
                    val direction = when (directionStr) {
                        "Upp" -> WatchType.DailyMoveDirection.UP
                        "Ned" -> WatchType.DailyMoveDirection.DOWN
                        "Båda" -> WatchType.DailyMoveDirection.BOTH
                        else -> WatchType.DailyMoveDirection.BOTH
                    }
                    if (threshold != null && threshold > 0) {
                        viewModel.createAlert(WatchType.DailyMove(threshold, direction), currentCompanyName())
                        Toast.makeText(context, "Dagsrörelse-bevakning skapad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Ange ett giltigt tröskelvärde", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                thresholdInput.requestFocus()
            }
    }

    fun showCreateKeyMetricsDialog() {
        val mainActivity = fragment.requireActivity() as? MainActivity ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_key_metrics, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.metricTypeInput)
        val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.targetValueInput)

        tickerInput.setText("$symbol - ${currentCompanyName()}", false)
        tickerInput.isEnabled = false

        val historyCard = dialogView.findViewById<CardView>(R.id.historyCard)
        historyCard.visibility = View.GONE

        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        val metricTypeAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, metricTypes)
        metricTypeInput.setAdapter(metricTypeAdapter)

        MaterialAlertDialogBuilder(context)
            .setTitle("Skapa nyckeltalsbevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa") { _, _ ->
                val metricTypeStr = metricTypeInput.text.toString()
                val targetValueStr = targetValueInput.text.toString()
                if (metricTypeStr.isNotEmpty() && targetValueStr.isNotEmpty()) {
                    val metricType = when (metricTypeStr) {
                        "P/E-tal" -> WatchType.MetricType.PE_RATIO
                        "P/S-tal" -> WatchType.MetricType.PS_RATIO
                        "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                        else -> null
                    }
                    val targetValue = targetValueStr.parseDecimal()
                    if (metricType != null && targetValue != null && targetValue > 0) {
                        viewModel.createAlert(
                            WatchType.KeyMetrics(metricType, targetValue, WatchType.PriceDirection.ABOVE),
                            currentCompanyName()
                        )
                        Toast.makeText(context, "Nyckeltalsbevakning skapad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Ange giltiga värden för alla fält", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                targetValueInput.requestFocus()
            }
    }

    // -------------------------------------------------------------------------
    // Edit-dispatcher och gemensamma dialogs
    // -------------------------------------------------------------------------

    fun showEditWatchItemDialog(item: WatchItem) {
        hideQuickActions()
        when (item.watchType) {
            is WatchType.PricePair -> showEditPricePairDialog(item)
            is WatchType.PriceTarget -> showEditPriceTargetDialog(item)
            is WatchType.PriceRange -> showEditPriceRangeDialog(item)
            is WatchType.DailyMove -> showEditDailyMoveDialog(item)
            is WatchType.ATHBased -> showEditDrawdownDialog(item)
            is WatchType.KeyMetrics -> showEditKeyMetricsDialog(item)
            is WatchType.Combined -> showEditCombinedAlertDialog(item)
            else -> {
                showQuickActions()
                Toast.makeText(context, "Redigering för denna bevakningstyp stöds inte ännu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showDeleteConfirmation(watchItem: WatchItem) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Ta bort bevakning")
            .setMessage("Är du säker på att du vill ta bort denna bevakning?")
            .setPositiveButton("Ta bort") { _, _ ->
                viewModel.deleteAlert(watchItem)
                Toast.makeText(context, "Bevakning borttagen", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    fun showEditNoteDialog() {
        val currentNote = (viewModel.noteState.value)?.note ?: ""
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_note, null)
        val noteInput = dialogView.findViewById<TextInputEditText>(R.id.noteInput)
        noteInput.setText(currentNote)

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.notes_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_button_update) { _, _ ->
                viewModel.saveNote(noteInput.text.toString())
            }
            .setNegativeButton(R.string.dialog_button_cancel, null)

        if (currentNote.isNotBlank()) {
            builder.setNeutralButton(R.string.dialog_button_remove) { _, _ ->
                viewModel.saveNote("")
            }
        }

        builder.show().also { dialog ->
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            noteInput.requestFocus()
        }
    }

    // -------------------------------------------------------------------------
    // Edit-dialoger (privata)
    // -------------------------------------------------------------------------

    private fun showEditPricePairDialog(item: WatchItem) {
        if (item.watchType !is WatchType.PricePair) return
        val pricePair = item.watchType
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_stock_pair, null)
        val ticker1Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker1Input).apply { setText(item.ticker1) }
        val ticker2Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker2Input).apply { setText(item.ticker2) }
        val priceDifferenceInput = dialogView.findViewById<TextInputEditText>(R.id.priceDifferenceInput).apply {
            setText(CurrencyHelper.formatDecimal(pricePair.priceDifference))
        }
        val notifyWhenEqualCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.notifyWhenEqualCheckbox).apply {
            isChecked = pricePair.notifyWhenEqual
        }

        val adapter1 = createStockAdapter()
        val adapter2 = createStockAdapter()
        setupStockSearch(ticker1Input, adapter1, stockSearchViewModel, includeCrypto = false)
        setupStockSearch(ticker2Input, adapter2, stockSearchViewModel2, includeCrypto = false)

        var selectedStock1: StockSearchResult? = null
        var selectedStock2: StockSearchResult? = null

        ticker1Input.setOnItemClickListener { _, _, position, _ -> selectedStock1 = adapter1.getItem(position) }
        ticker2Input.setOnItemClickListener { _, _, position, _ -> selectedStock2 = adapter2.getItem(position) }

        MaterialAlertDialogBuilder(context)
            .setTitle("Redigera aktiepar")
            .setView(dialogView)
            .setOnDismissListener { showQuickActions() }
            .setPositiveButton("Uppdatera") { _, _ ->
                val ticker1Str = ticker1Input.text.toString().trim()
                val ticker2Str = ticker2Input.text.toString().trim()
                val priceDifferenceStr = priceDifferenceInput.text.toString()
                val notifyWhenEqual = notifyWhenEqualCheckbox.isChecked
                val finalTicker1 = selectedStock1?.symbol ?: ticker1Str
                val finalTicker2 = selectedStock2?.symbol ?: ticker2Str
                if (finalTicker1.isNotEmpty() && finalTicker2.isNotEmpty() && priceDifferenceStr.isNotEmpty()) {
                    val priceDifference = priceDifferenceStr.parseDecimal() ?: 0.0
                    val updatedItem = item.copy(
                        watchType = WatchType.PricePair(priceDifference, notifyWhenEqual),
                        ticker1 = finalTicker1,
                        ticker2 = finalTicker2,
                        companyName1 = selectedStock1?.name ?: item.companyName1,
                        companyName2 = selectedStock2?.name ?: item.companyName2
                    )
                    viewModel.updateWatchItem(updatedItem)
                    Toast.makeText(context, "Aktiepar uppdaterat", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ -> showDeleteConfirmation(item) }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                priceDifferenceInput.requestFocus()
            }
    }

    private fun showEditPriceTargetDialog(item: WatchItem, currentPrice: Double = 0.0) {
        if (item.watchType !is WatchType.PriceTarget) return
        val currencySymbol = currentCurrencySymbol()
        val priceTarget = item.watchType
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput.parent as? TextInputLayout
        tickerInputLayout?.visibility = View.GONE
        tickerInput.setText(item.ticker ?: "")
        tickerInput.isEnabled = false

        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput).apply {
            setText(CurrencyHelper.formatDecimal(priceTarget.targetPrice))
        }
        dialogView.findViewById<TextInputLayout>(R.id.targetPriceLayout)?.hint = "Målpris ($currencySymbol)"

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Redigera prisbevakning")
            .setView(dialogView)
            .setOnDismissListener { showQuickActions() }
            .setPositiveButton("Uppdatera") { _, _ ->
                val targetPriceStr = targetPriceInput.text.toString()
                if (targetPriceStr.isNotEmpty()) {
                    val targetPrice = targetPriceStr.parseDecimal()
                    if (targetPrice != null && targetPrice > 0) {
                        val existingDirection = (item.watchType as WatchType.PriceTarget).direction
                        val direction = when {
                            currentPrice > 0.0 && currentPrice >= targetPrice -> WatchType.PriceDirection.BELOW
                            currentPrice > 0.0 -> WatchType.PriceDirection.ABOVE
                            else -> existingDirection
                        }
                        val updatedItem = item.copy(watchType = WatchType.PriceTarget(targetPrice, direction))
                        viewModel.updateWatchItem(updatedItem)
                        Toast.makeText(context, "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Ange ett giltigt målpris", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Ange ett målpris", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ -> showDeleteConfirmation(item) }
            .setNegativeButton("Avbryt", null)
            .create()
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        targetPriceInput.requestFocus()
    }

    private fun showEditPriceRangeDialog(item: WatchItem) {
        val currency = currentCurrency()
        showEditPriceRangeDialog(
            context = context,
            item = item,
            currency = currency,
            onUpdate = { minPrice, maxPrice ->
                val updatedItem = item.copy(watchType = WatchType.PriceRange(minPrice, maxPrice))
                viewModel.updateWatchItem(updatedItem)
                Toast.makeText(context, context.getString(R.string.toast_watch_updated), Toast.LENGTH_SHORT).show()
            },
            onDelete = { showDeleteConfirmation(item) },
            onDismiss = { showQuickActions() }
        )
    }

    private fun showEditDailyMoveDialog(item: WatchItem) {
        if (item.watchType !is WatchType.DailyMove) return
        val dailyMove = item.watchType
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_daily_move, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput?.parent as? TextInputLayout
        tickerInputLayout?.visibility = View.GONE
        tickerInput?.setText(item.ticker ?: "")
        tickerInput?.isEnabled = false

        val thresholdInput = dialogView.findViewById<TextInputEditText>(R.id.thresholdInput).apply {
            setText(CurrencyHelper.formatDecimal(dailyMove.percentThreshold))
        }
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput).apply {
            setText(when (dailyMove.direction) {
                WatchType.DailyMoveDirection.UP -> "Upp"
                WatchType.DailyMoveDirection.DOWN -> "Ned"
                WatchType.DailyMoveDirection.BOTH -> "Båda"
            })
        }

        val directions = arrayOf("Upp", "Ned", "Båda")
        val directionAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Redigera dagsrörelse-bevakning")
            .setView(dialogView)
            .setOnDismissListener { showQuickActions() }
            .setPositiveButton("Uppdatera") { _, _ ->
                val thresholdStr = thresholdInput.text.toString()
                val directionStr = directionInput.text.toString()
                if (thresholdStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val threshold = thresholdStr.parseDecimal()
                    val direction = when (directionStr) {
                        "Upp" -> WatchType.DailyMoveDirection.UP
                        "Ned" -> WatchType.DailyMoveDirection.DOWN
                        "Båda" -> WatchType.DailyMoveDirection.BOTH
                        else -> WatchType.DailyMoveDirection.BOTH
                    }
                    if (threshold != null && threshold > 0) {
                        val updatedItem = item.copy(watchType = WatchType.DailyMove(threshold, direction))
                        viewModel.updateWatchItem(updatedItem)
                        Toast.makeText(context, "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Ange ett giltigt tröskelvärde", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ -> showDeleteConfirmation(item) }
            .setNegativeButton("Avbryt", null)
            .create()
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        thresholdInput.requestFocus()
    }

    private fun showEditDrawdownDialog(item: WatchItem) {
        if (item.watchType !is WatchType.ATHBased) return
        val currencySymbol = currentCurrencySymbol()
        val athBased = item.watchType
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput.parent as? TextInputLayout
        tickerInputLayout?.visibility = View.GONE
        tickerInput.setText(item.ticker ?: "")
        tickerInput.isEnabled = false

        val absoluteLabel = "Absolut ($currencySymbol)"
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput).apply {
            setText(when (athBased.dropType) {
                WatchType.DropType.PERCENTAGE -> "Procent"
                WatchType.DropType.ABSOLUTE -> absoluteLabel
            })
        }
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput).apply {
            setText(CurrencyHelper.formatDecimal(athBased.dropValue))
        }

        val dropTypes = arrayOf("Procent", absoluteLabel)
        val dropTypeAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, dropTypes)
        dropTypeInput.setAdapter(dropTypeAdapter)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Redigera drawdown-bevakning")
            .setView(dialogView)
            .setOnDismissListener { showQuickActions() }
            .setPositiveButton("Uppdatera") { _, _ ->
                val dropTypeStr = dropTypeInput.text.toString()
                val dropValueStr = dropValueInput.text.toString()
                if (dropTypeStr.isNotEmpty() && dropValueStr.isNotEmpty()) {
                    val dropType = when (dropTypeStr) {
                        "Procent" -> WatchType.DropType.PERCENTAGE
                        else -> WatchType.DropType.ABSOLUTE
                    }
                    val dropValue = dropValueStr.parseDecimal()
                    if (dropValue != null && dropValue > 0) {
                        val updatedItem = item.copy(watchType = WatchType.ATHBased(dropType, dropValue))
                        viewModel.updateWatchItem(updatedItem)
                        Toast.makeText(context, "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Ange ett giltigt värde", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ -> showDeleteConfirmation(item) }
            .setNegativeButton("Avbryt", null)
            .create()
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dropValueInput.requestFocus()
    }

    private fun showEditKeyMetricsDialog(item: WatchItem, currentMetricValue: Double = 0.0) {
        if (item.watchType !is WatchType.KeyMetrics) return
        val keyMetrics = item.watchType
        val itemSymbol = item.ticker ?: symbol
        val itemCompanyName = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
            ?: item.companyName ?: ""

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_key_metrics, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput.parent as? TextInputLayout
        tickerInputLayout?.visibility = View.GONE
        tickerInput.setText("$itemSymbol - $itemCompanyName")
        tickerInput.isEnabled = false

        val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.metricTypeInput).apply {
            setText(when (keyMetrics.metricType) {
                WatchType.MetricType.PE_RATIO -> "P/E-tal"
                WatchType.MetricType.PS_RATIO -> "P/S-tal"
                WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
            })
        }
        val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.targetValueInput).apply {
            setText(CurrencyHelper.formatDecimal(keyMetrics.targetValue))
        }

        val historyCard = dialogView.findViewById<CardView>(R.id.historyCard)
        historyCard.visibility = View.GONE

        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        val metricTypeAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, metricTypes)
        metricTypeInput.setAdapter(metricTypeAdapter)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Redigera nyckeltalsbevakning")
            .setView(dialogView)
            .setOnDismissListener { showQuickActions() }
            .setPositiveButton("Uppdatera") { _, _ ->
                val metricTypeStr = metricTypeInput.text.toString()
                val targetValueStr = targetValueInput.text.toString()
                if (metricTypeStr.isNotEmpty() && targetValueStr.isNotEmpty()) {
                    val metricType = when (metricTypeStr) {
                        "P/E-tal" -> WatchType.MetricType.PE_RATIO
                        "P/S-tal" -> WatchType.MetricType.PS_RATIO
                        "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                        else -> null
                    }
                    val targetValue = targetValueStr.parseDecimal()
                    if (metricType != null && targetValue != null && targetValue > 0) {
                        val existingMetricDirection = (item.watchType as WatchType.KeyMetrics).direction
                        val direction = when {
                            currentMetricValue > 0.0 && currentMetricValue >= targetValue -> WatchType.PriceDirection.BELOW
                            currentMetricValue > 0.0 -> WatchType.PriceDirection.ABOVE
                            else -> existingMetricDirection
                        }
                        val updatedItem = item.copy(watchType = WatchType.KeyMetrics(metricType, targetValue, direction))
                        viewModel.updateWatchItem(updatedItem)
                        Toast.makeText(context, "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Ange giltiga värden", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ -> showDeleteConfirmation(item) }
            .setNegativeButton("Avbryt", null)
            .create()
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        targetValueInput.requestFocus()
    }

    private fun showEditCombinedAlertDialog(watchItem: WatchItem) {
        val combined = watchItem.watchType as? WatchType.Combined ?: return
        val expression = combined.expression

        val decompositionResult = decomposeExpression(expression)
        if (decompositionResult == null) {
            showQuickActions()
            Toast.makeText(context, "Detta kombinerat larm kan inte redigeras (komplex struktur)", Toast.LENGTH_LONG).show()
            return
        }

        val (itemSymbol, conditions) = decompositionResult

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_combined_alert, null)
        val symbolInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.symbolInput)
        val conditionsRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.conditionsRecyclerView)
        val addConditionButton = dialogView.findViewById<MaterialButton>(R.id.addConditionButton)
        val previewText = dialogView.findViewById<TextView>(R.id.previewText)

        conditionsRecyclerView.layoutManager = LinearLayoutManager(context)

        lateinit var conditionAdapter: ConditionBuilderAdapter

        conditionAdapter = ConditionBuilderAdapter(
            onConditionTypeChanged = { _, _ ->
                updatePreview(conditionAdapter, symbolInput.text.toString(), previewText)
            },
            onValueChanged = { _, _ ->
                updatePreview(conditionAdapter, symbolInput.text.toString(), previewText)
            },
            onOperatorChanged = { _, _ ->
                updatePreview(conditionAdapter, symbolInput.text.toString(), previewText)
            },
            onRemove = { position ->
                conditionAdapter.removeCondition(position)
                updatePreview(conditionAdapter, symbolInput.text.toString(), previewText)
            }
        )

        conditionAdapter.setConditions(conditions)
        conditionsRecyclerView.adapter = conditionAdapter

        val stockAdapter = createStockAdapter()
        symbolInput.setAdapter(stockAdapter)
        symbolInput.setText(itemSymbol, false)

        setupStockSearch(symbolInput, stockAdapter, stockSearchViewModel, includeCrypto = true)

        symbolInput.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = stockAdapter.getItem(position)
            val newSymbol = selectedItem?.symbol ?: symbolInput.text.toString()
            updatePreview(conditionAdapter, newSymbol, previewText)
        }

        addConditionButton.setOnClickListener {
            conditionAdapter.addCondition()
            updatePreview(conditionAdapter, symbolInput.text.toString(), previewText)
        }

        updatePreview(conditionAdapter, itemSymbol, previewText)

        MaterialAlertDialogBuilder(context)
            .setTitle("Redigera kombinerat larm")
            .setView(dialogView)
            .setPositiveButton("Spara") { _, _ ->
                val newSymbol = symbolInput.text.toString().trim()
                val newConditions = conditionAdapter.getConditions()

                if (newSymbol.isEmpty()) {
                    Toast.makeText(context, "Välj en aktie", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newConditions.isEmpty()) {
                    Toast.makeText(context, "Lägg till minst ett villkor", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val validConditions = newConditions.filter {
                    it.value.isNotEmpty() && it.value.parseDecimal() != null
                }

                if (validConditions.size != newConditions.size) {
                    Toast.makeText(context, "Alla villkor måste ha giltigt värde", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newExpression = buildAlertExpression(newSymbol, validConditions)
                if (newExpression == null) {
                    Toast.makeText(context, "Kunde inte skapa uttryck", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    try {
                        val updatedWatchItem = watchItem.copy(
                            watchType = WatchType.Combined(newExpression),
                            ticker = newSymbol
                        )
                        viewModel.updateWatchItem(updatedWatchItem)
                        Toast.makeText(context, "Kombinerat larm uppdaterat", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Kunde inte uppdatera kombinerat larm: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNeutralButton("Ta bort") { _, _ -> showDeleteConfirmation(watchItem) }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // Combined alert-hjälpare
    // -------------------------------------------------------------------------

    private fun decomposeExpression(expression: AlertExpression): Pair<String, List<ConditionBuilderAdapter.ConditionData>>? {
        val conditions = mutableListOf<ConditionBuilderAdapter.ConditionData>()
        var currentSymbol: String? = null

        fun extractRules(expr: AlertExpression, operator: String? = null): Boolean {
            return when (expr) {
                is AlertExpression.Single -> {
                    val rule = expr.rule
                    val ruleSymbol = when (rule) {
                        is AlertRule.SinglePrice -> rule.symbol
                        is AlertRule.SingleDrawdownFromHigh -> rule.symbol
                        is AlertRule.SingleDailyMove -> rule.symbol
                        is AlertRule.SingleKeyMetric -> rule.symbol
                        is AlertRule.PairSpread -> return false
                    }

                    if (currentSymbol == null) {
                        currentSymbol = ruleSymbol
                    } else if (currentSymbol != ruleSymbol) {
                        return false
                    }

                    val conditionData = when (rule) {
                        is AlertRule.SinglePrice -> ConditionBuilderAdapter.ConditionData(
                            conditionType = "Pris",
                            direction = if (rule.comparisonType == AlertRule.PriceComparisonType.ABOVE) "Över" else "Under",
                            value = rule.priceLimit.toString(),
                            operator = operator
                        )
                        is AlertRule.SingleDrawdownFromHigh -> ConditionBuilderAdapter.ConditionData(
                            conditionType = "52w High Drop",
                            direction = "Över",
                            value = rule.dropValue.toString(),
                            operator = operator
                        )
                        is AlertRule.SingleDailyMove -> ConditionBuilderAdapter.ConditionData(
                            conditionType = "Dagsrörelse",
                            direction = "Över",
                            value = rule.percentThreshold.toString(),
                            operator = operator
                        )
                        is AlertRule.SingleKeyMetric -> {
                            val conditionType = when (rule.metricType) {
                                AlertRule.KeyMetricType.PE_RATIO -> "P/E-tal"
                                AlertRule.KeyMetricType.PS_RATIO -> "P/S-tal"
                                AlertRule.KeyMetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
                            }
                            ConditionBuilderAdapter.ConditionData(
                                conditionType = conditionType,
                                direction = if (rule.direction == AlertRule.PriceComparisonType.ABOVE) "Över" else "Under",
                                value = rule.targetValue.toString(),
                                operator = operator
                            )
                        }
                        else -> return false
                    }

                    conditions.add(conditionData)
                    true
                }
                is AlertExpression.And -> {
                    val leftOk = extractRules(expr.left, null)
                    if (!leftOk) return false
                    extractRules(expr.right, "OCH")
                }
                is AlertExpression.Or -> {
                    val leftOk = extractRules(expr.left, null)
                    if (!leftOk) return false
                    extractRules(expr.right, "ELLER")
                }
                is AlertExpression.Not -> false
            }
        }

        val success = extractRules(expression)
        if (!success || currentSymbol == null || conditions.isEmpty()) return null

        if (conditions.isNotEmpty()) {
            conditions[0].operator = null
        }

        return Pair(currentSymbol!!, conditions.toList())
    }

    private fun updatePreview(
        adapter: ConditionBuilderAdapter,
        currentSymbol: String,
        previewText: TextView
    ) {
        val conditions = adapter.getConditions()
        if (currentSymbol.isEmpty()) {
            previewText.text = "Välj en aktie"
            previewText.setTextColor(context.getColor(android.R.color.darker_gray))
            return
        }
        if (conditions.isEmpty()) {
            previewText.text = "Lägg till minst ett villkor"
            previewText.setTextColor(context.getColor(android.R.color.darker_gray))
            return
        }
        val expression = buildAlertExpression(currentSymbol, conditions)
        if (expression != null) {
            previewText.text = expression.getDescription()
            previewText.setTextColor(context.getColor(android.R.color.black))
        } else {
            previewText.text = "Ofullständiga villkor"
            previewText.setTextColor(context.getColor(android.R.color.holo_red_dark))
        }
    }

    private fun buildAlertExpression(
        currentSymbol: String,
        conditions: List<ConditionBuilderAdapter.ConditionData>
    ): AlertExpression? {
        if (conditions.isEmpty()) return null

        val rules = conditions.mapNotNull { condition -> buildAlertRule(currentSymbol, condition) }
        if (rules.isEmpty()) return null

        var expression: AlertExpression = AlertExpression.Single(rules.first())

        for (i in 1 until rules.size) {
            val nextExpression = AlertExpression.Single(rules[i])
            val operator = conditions[i].operator ?: "OCH"
            expression = if (operator.contains("OCH")) {
                AlertExpression.And(expression, nextExpression)
            } else {
                AlertExpression.Or(expression, nextExpression)
            }
        }

        return expression
    }

    private fun buildAlertRule(currentSymbol: String, condition: ConditionBuilderAdapter.ConditionData): AlertRule? {
        val value = condition.value.parseDecimal() ?: return null

        return when (condition.conditionType) {
            "Pris" -> {
                val comparisonType = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SinglePrice(currentSymbol, comparisonType, value)
            }
            "P/E-tal" -> {
                val direction = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SingleKeyMetric(currentSymbol, AlertRule.KeyMetricType.PE_RATIO, value, direction)
            }
            "P/S-tal" -> {
                val direction = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SingleKeyMetric(currentSymbol, AlertRule.KeyMetricType.PS_RATIO, value, direction)
            }
            "Utdelningsprocent" -> {
                val direction = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SingleKeyMetric(currentSymbol, AlertRule.KeyMetricType.DIVIDEND_YIELD, value, direction)
            }
            "52w High Drop" -> AlertRule.SingleDrawdownFromHigh(currentSymbol, AlertRule.DrawdownDropType.PERCENTAGE, value)
            "Dagsrörelse" -> AlertRule.SingleDailyMove(currentSymbol, value, AlertRule.DailyMoveDirection.BOTH)
            else -> null
        }
    }

    // -------------------------------------------------------------------------
    // Stock search-hjälpare
    // -------------------------------------------------------------------------

    private fun createStockAdapter(): ArrayAdapter<StockSearchResult> {
        return object : ArrayAdapter<StockSearchResult>(
            context,
            R.layout.dropdown_item_with_icon,
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                createAdapterItemView(position, convertView, parent)

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                createAdapterItemView(position, convertView, parent)

            private fun createAdapterItemView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view: View = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.dropdown_item_with_icon, parent, false)
                val item: StockSearchResult? = getItem(position)
                if (item != null) {
                    val textView = view.findViewById<TextView>(R.id.text)
                    val iconView = view.findViewById<ImageView>(R.id.icon)
                    textView.text = "${item.symbol} - ${item.name}"
                    if (item.isCrypto) {
                        iconView.setImageResource(R.drawable.ic_crypto)
                        iconView.visibility = View.VISIBLE
                    } else {
                        iconView.setImageResource(R.drawable.ic_stock)
                        iconView.visibility = View.VISIBLE
                    }
                }
                return view
            }

            override fun getFilter(): android.widget.Filter {
                return object : android.widget.Filter() {
                    override fun performFiltering(constraint: CharSequence?): android.widget.Filter.FilterResults {
                        return android.widget.Filter.FilterResults().apply {
                            values = mutableListOf<StockSearchResult>()
                            count = 0
                        }
                    }
                    override fun publishResults(constraint: CharSequence?, results: android.widget.Filter.FilterResults?) {
                        // Filtrering hanteras via ViewModel
                    }
                }
            }
        }
    }

    private fun setupStockSearch(
        input: MaterialAutoCompleteTextView,
        adapter: ArrayAdapter<StockSearchResult>,
        searchViewModel: StockSearchViewModel,
        includeCrypto: Boolean = true
    ) {
        input.threshold = 2
        input.setAdapter(adapter)

        lifecycleScope.launch {
            searchViewModel.searchState.collect { state ->
                when (state) {
                    is SearchState.Loading -> Unit
                    is SearchState.Success -> {
                        adapter.clear()
                        adapter.addAll(state.results)
                        adapter.notifyDataSetChanged()
                        if (state.results.isNotEmpty() && input.text.isNotEmpty()) {
                            input.post {
                                if (input.hasFocus()) input.showDropDown()
                            }
                        }
                    }
                    is SearchState.Error -> {
                        adapter.clear()
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }

        var textChangeJob: Job? = null

        input.doAfterTextChanged { text ->
            textChangeJob?.cancel()
            if (text.isNullOrEmpty()) {
                adapter.clear()
                adapter.notifyDataSetChanged()
                input.dismissDropDown()
                return@doAfterTextChanged
            }
            textChangeJob = lifecycleScope.launch {
                delay(300)
                searchViewModel.search(text.toString(), includeCrypto)
            }
        }

        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && input.text.isNotEmpty() && adapter.count > 0) {
                input.post { input.showDropDown() }
            }
        }
    }
}
