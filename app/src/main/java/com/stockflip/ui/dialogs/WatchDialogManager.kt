package com.stockflip.ui.dialogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
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
import com.google.android.material.chip.Chip
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
import com.stockflip.armedConditionDescription
import com.stockflip.repository.SearchState
import com.stockflip.ui.builders.ConditionBuilderAdapter
import com.stockflip.viewmodel.StockSearchViewModel
import kotlinx.coroutines.CancellationException
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
    private val companyName: String?,
    private val onWatchChanged: () -> Unit = {}
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

    private fun currentStockData(): StockDetailData? =
        (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data

    private fun currentMetricHistory(metricType: WatchType.MetricType) =
        viewModel.metricHistoryState.value[metricType]

    private fun currentCompanyName(): String =
        (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
            ?: companyName ?: ""

    private fun setPresetChip(
        chip: Chip,
        label: String,
        value: Double,
        suffix: String = "",
        onApply: (Double) -> Unit
    ) {
        chip.text = if (suffix.isNotEmpty()) "$label$suffix" else label
        chip.setOnClickListener { onApply(value) }
    }

    private fun metricLabel(metricType: WatchType.MetricType): String {
	        return when (metricType) {
	            WatchType.MetricType.PE_RATIO -> "P/E"
	            WatchType.MetricType.PS_RATIO -> "P/S"
	            WatchType.MetricType.DIVIDEND_YIELD -> "utdelning"
	            WatchType.MetricType.EARNINGS_PER_SHARE -> "vinst/aktie"
	        }
    }

    private fun formatMetricValue(metricType: WatchType.MetricType, value: Double): String {
        return when (metricType) {
            WatchType.MetricType.DIVIDEND_YIELD -> "${CurrencyHelper.formatDecimal(value)}%"
            else -> CurrencyHelper.formatDecimal(value)
        }
    }

    private val editor by lazy {
        WatchItemEditor(
            context = context,
            scope = lifecycleScope,
            stockSearchViewModel = stockSearchViewModel,
            stockSearchViewModel2 = stockSearchViewModel2,
            allowSymbolEditing = false,
            createStockAdapter = { createStockAdapter() },
            setupStockSearch = { input, adapter, searchViewModel, includeCrypto ->
                setupStockSearch(input, adapter, searchViewModel, includeCrypto)
            },
            onUpdateWatchItem = { updatedItem ->
                viewModel.updateWatchItem(updatedItem).also { succeeded ->
                    if (succeeded) onWatchChanged()
                }
            },
            onDeleteRequested = { watchItem ->
                showDeleteConfirmation(watchItem)
            },
            onBeforeDialog = { hideQuickActions() },
            onDialogDismissed = { showQuickActions() },
            currentCurrencyFor = { currentCurrency() },
            currentPriceFor = {
                (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.lastPrice
            },
            currentMetricValueFor = { watchItem ->
                val data = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data ?: return@WatchItemEditor null
                val metricType = (watchItem.watchType as? WatchType.KeyMetrics)?.metricType ?: return@WatchItemEditor null
                when (metricType) {
	                    WatchType.MetricType.PE_RATIO -> data.peRatio
	                    WatchType.MetricType.PS_RATIO -> data.psRatio
	                    WatchType.MetricType.DIVIDEND_YIELD -> data.dividendYield
	                    WatchType.MetricType.EARNINGS_PER_SHARE -> data.earningsPerShare
	                }
            }
        )
    }

    // -------------------------------------------------------------------------
    // Create-dialoger
    // -------------------------------------------------------------------------

    fun showCreatePriceTargetDialog(suggestedTargetPrice: Double? = null) {
        val currencySymbol = currentCurrencySymbol()
        val stockData = currentStockData()
        val currentPrice = stockData?.lastPrice
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput)
        val contextText = dialogView.findViewById<TextView>(R.id.contextText)
        val triggerInfoText = dialogView.findViewById<TextView>(R.id.triggerInfoText)
        val presetChipOne = dialogView.findViewById<Chip>(R.id.presetChipOne)
        val presetChipTwo = dialogView.findViewById<Chip>(R.id.presetChipTwo)
        val presetChipThree = dialogView.findViewById<Chip>(R.id.presetChipThree)
        val presetChipFour = dialogView.findViewById<Chip>(R.id.presetChipFour)

        tickerInput?.parent?.let { parent ->
            if (parent is TextInputLayout) {
                parent.visibility = View.GONE
            } else if (parent is ViewGroup) {
                parent.visibility = View.GONE
            }
        }
        dialogView.findViewById<TextInputLayout>(R.id.targetPriceLayout)?.hint = "Målpris ($currencySymbol)"
        suggestedTargetPrice?.let { targetPriceInput.setText(CurrencyHelper.formatDecimal(it)) }
        contextText.text = currentPrice?.let {
            "Nuvarande pris ${CurrencyHelper.formatPrice(it, currentCurrency())}. Välj en nivå under eller över dagens kurs."
        } ?: "Nuvarande pris saknas just nu. Ange den nivå du vill bevaka."
        triggerInfoText.text = "Appen väljer själv om kursen ska bevakas över eller under målpriset utifrån aktuell kurs."

        currentPrice?.let { price ->
            setPresetChip(
                chip = presetChipOne,
                label = "-5 % (${CurrencyHelper.formatPrice(price * 0.95, currentCurrency())})",
                value = price * 0.95
            ) { targetPriceInput.setText(CurrencyHelper.formatDecimal(it)) }
            setPresetChip(
                chip = presetChipTwo,
                label = "-10 % (${CurrencyHelper.formatPrice(price * 0.90, currentCurrency())})",
                value = price * 0.90
            ) { targetPriceInput.setText(CurrencyHelper.formatDecimal(it)) }
            setPresetChip(
                chip = presetChipThree,
                label = "+5 % (${CurrencyHelper.formatPrice(price * 1.05, currentCurrency())})",
                value = price * 1.05
            ) { targetPriceInput.setText(CurrencyHelper.formatDecimal(it)) }
            setPresetChip(
                chip = presetChipFour,
                label = "+10 % (${CurrencyHelper.formatPrice(price * 1.10, currentCurrency())})",
                value = price * 1.10
            ) { targetPriceInput.setText(CurrencyHelper.formatDecimal(it)) }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Skapa målpris-bevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa", null)
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(targetPriceInput, selectAll = false)
                dialog.setPositiveActionKeepingOpen(lifecycleScope) {
                    val targetPrice = when (val input = validatePositiveDecimal(
                        targetPriceInput.text?.toString(), "Ange ett målpris", "Ange ett giltigt målpris"
                    )) {
                        is DecimalInput.Valid -> input.value
                        is DecimalInput.Invalid -> {
                            targetPriceInput.showFieldError(input.message)
                            return@setPositiveActionKeepingOpen false
                        }
                    }
                    val latestPrice = currentStockData()?.lastPrice ?: 0.0
                    val direction = if (latestPrice > 0.0 && latestPrice >= targetPrice)
                        WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
                    val watchType = WatchType.PriceTarget(targetPrice, direction)
                    val preview = WatchItem(watchType = watchType, ticker = symbol, companyName = currentCompanyName())
                    saveNewWatch(
                        watchType,
                        errorField = targetPriceInput,
                        successMessage = "Skapad: ${preview.armedConditionDescription(currentCurrency())}",
                        toastDuration = Toast.LENGTH_LONG
                    )
                }
            }
    }

    fun showCreateDrawdownDialog(
        suggestedDropType: WatchType.DropType = WatchType.DropType.PERCENTAGE,
        suggestedDropValue: Double? = null,
        suggestedReference: WatchType.HighReference = WatchType.HighReference.FIFTY_TWO_WEEK_HIGH
    ) {
        val currencySymbol = currentCurrencySymbol()
        val stockData = currentStockData()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val highReferenceInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.highReferenceInput)
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput)
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput)
        val dropValueLayout = dialogView.findViewById<TextInputLayout>(R.id.dropValueLayout)
        val contextText = dialogView.findViewById<TextView>(R.id.contextText)
        val triggerInfoText = dialogView.findViewById<TextView>(R.id.triggerInfoText)
        val presetChipOne = dialogView.findViewById<Chip>(R.id.presetChipOne)
        val presetChipTwo = dialogView.findViewById<Chip>(R.id.presetChipTwo)
        val presetChipThree = dialogView.findViewById<Chip>(R.id.presetChipThree)
        val presetChipFour = dialogView.findViewById<Chip>(R.id.presetChipFour)

        tickerInput?.parent?.let { parent ->
            if (parent is TextInputLayout) {
                parent.visibility = View.GONE
            } else if (parent is ViewGroup) {
                parent.visibility = View.GONE
            }
        }

        fun referenceLabel(reference: WatchType.HighReference): String = when (reference) {
            WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> "52v högsta"
            WatchType.HighReference.ALL_TIME_HIGH -> "Historiskt högsta"
        }

        fun selectedReference(): WatchType.HighReference {
            return when (highReferenceInput.text.toString()) {
                "Historiskt högsta" -> WatchType.HighReference.ALL_TIME_HIGH
                else -> WatchType.HighReference.FIFTY_TWO_WEEK_HIGH
            }
        }

        fun selectedDropType(): WatchType.DropType {
            return if (dropTypeInput.text.toString() == "Procent") {
                WatchType.DropType.PERCENTAGE
            } else {
                WatchType.DropType.ABSOLUTE
            }
        }

        fun referenceHigh(reference: WatchType.HighReference): Double? = when (reference) {
            WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> stockData?.week52High
            WatchType.HighReference.ALL_TIME_HIGH -> stockData?.allTimeHigh
        }

        fun referenceDrawdown(reference: WatchType.HighReference): Double? = when (reference) {
            WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> stockData?.drawdownPercent
            WatchType.HighReference.ALL_TIME_HIGH -> stockData?.allTimeDrawdownPercent
        }

        val references = arrayOf(referenceLabel(WatchType.HighReference.FIFTY_TWO_WEEK_HIGH), referenceLabel(WatchType.HighReference.ALL_TIME_HIGH))
        highReferenceInput.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, references))
        highReferenceInput.setText(referenceLabel(suggestedReference), false)

        val dropTypes = arrayOf("Procent", "Absolut ($currencySymbol)")
        val dropTypeAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, dropTypes)
        dropTypeInput.setAdapter(dropTypeAdapter)
        dropTypeInput.setText(
            if (suggestedDropType == WatchType.DropType.PERCENTAGE) "Procent" else "Absolut ($currencySymbol)",
            false
        )
        suggestedDropValue?.let { dropValueInput.setText(CurrencyHelper.formatDecimal(it)) }
        triggerInfoText.text = "När nedgångsnivån nås markeras larmet som utlöst och kan återaktiveras senare."

        fun applyDrawdownContext(reference: WatchType.HighReference, dropType: WatchType.DropType) {
            contextText.text = buildString {
                append("Aktuell drawdown ")
                append(referenceDrawdown(reference)?.let { "${CurrencyHelper.formatDecimal(it)}%" } ?: "saknas")
                referenceHigh(reference)?.let {
                    append(" från ${referenceLabel(reference).lowercase()} ${CurrencyHelper.formatPrice(it, currentCurrency())}")
                }
                append(".")
            }
            if (dropType == WatchType.DropType.PERCENTAGE) {
                dropValueLayout.hint = "Nedgångsvärde (%)"
                setPresetChip(presetChipOne, "5 %", 5.0) {
                    dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                }
                setPresetChip(presetChipTwo, "10 %", 10.0) {
                    dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                }
                setPresetChip(presetChipThree, "15 %", 15.0) {
                    dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                }
                setPresetChip(presetChipFour, "20 %", 20.0) {
                    dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                }
            } else {
                dropValueLayout.hint = "Nedgångsvärde ($currencySymbol)"
                val high = referenceHigh(reference)
                if (high != null && high > 0.0) {
                    val values = listOf(high * 0.05, high * 0.10, high * 0.15, high * 0.20)
                    setPresetChip(presetChipOne, CurrencyHelper.formatPrice(values[0], currentCurrency()), values[0]) {
                        dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                    }
                    setPresetChip(presetChipTwo, CurrencyHelper.formatPrice(values[1], currentCurrency()), values[1]) {
                        dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                    }
                    setPresetChip(presetChipThree, CurrencyHelper.formatPrice(values[2], currentCurrency()), values[2]) {
                        dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                    }
                    setPresetChip(presetChipFour, CurrencyHelper.formatPrice(values[3], currentCurrency()), values[3]) {
                        dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                    }
                } else {
                    listOf(presetChipOne, presetChipTwo, presetChipThree, presetChipFour).forEach { chip ->
                        chip.text = "Värde saknas"
                        chip.setOnClickListener(null)
                    }
                }
            }
        }
        applyDrawdownContext(suggestedReference, suggestedDropType)
        highReferenceInput.doAfterTextChanged {
            applyDrawdownContext(selectedReference(), selectedDropType())
        }
        dropTypeInput.doAfterTextChanged {
            applyDrawdownContext(selectedReference(), selectedDropType())
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Skapa drawdown-bevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa", null)
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(dropValueInput, selectAll = false)
                dialog.setPositiveActionKeepingOpen(lifecycleScope) {
                    val dropTypeStr = dropTypeInput.text.toString()
                    if (dropTypeStr.isEmpty()) {
                        dropTypeInput.showFieldError("Välj procent eller belopp")
                        return@setPositiveActionKeepingOpen false
                    }
                    val dropType = when (dropTypeStr) {
                        "Procent" -> WatchType.DropType.PERCENTAGE
                        else -> WatchType.DropType.ABSOLUTE
                    }
                    val dropValue = when (val input = validatePositiveDecimal(
                        dropValueInput.text?.toString(), "Ange hur stort fallet ska vara", "Ange ett giltigt värde större än 0"
                    )) {
                        is DecimalInput.Valid -> input.value
                        is DecimalInput.Invalid -> {
                            dropValueInput.showFieldError(input.message)
                            return@setPositiveActionKeepingOpen false
                        }
                    }
                    saveNewWatch(
                        WatchType.ATHBased(dropType, dropValue, selectedReference()),
                        errorField = dropValueInput,
                        successMessage = "Drawdown-bevakning skapad"
                    )
                }
            }
    }

    fun showCreateDailyMoveDialog(
        suggestedThreshold: Double? = null,
        suggestedDirection: WatchType.DailyMoveDirection = WatchType.DailyMoveDirection.BOTH
    ) {
        val stockData = currentStockData()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_daily_move, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput?.parent as? TextInputLayout
        tickerInputLayout?.visibility = View.GONE
        tickerInput?.setText("$symbol - ${currentCompanyName()}")
        tickerInput?.isEnabled = false

        val thresholdInput = dialogView.findViewById<TextInputEditText>(R.id.thresholdInput)
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput)
        val contextText = dialogView.findViewById<TextView>(R.id.contextText)
        val triggerInfoText = dialogView.findViewById<TextView>(R.id.triggerInfoText)
        val presetChipOne = dialogView.findViewById<Chip>(R.id.presetChipOne)
        val presetChipTwo = dialogView.findViewById<Chip>(R.id.presetChipTwo)
        val presetChipThree = dialogView.findViewById<Chip>(R.id.presetChipThree)
        val presetChipFour = dialogView.findViewById<Chip>(R.id.presetChipFour)

        val directions = arrayOf("Upp", "Ned", "Båda")
        val directionAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)
        directionInput.setText(
            when (suggestedDirection) {
                WatchType.DailyMoveDirection.UP -> "Upp"
                WatchType.DailyMoveDirection.DOWN -> "Ned"
                WatchType.DailyMoveDirection.BOTH -> "Båda"
            },
            false
        )
        suggestedThreshold?.let { thresholdInput.setText(CurrencyHelper.formatDecimal(it)) }
        contextText.text = stockData?.dailyChangePercent?.let {
            val sign = if (it >= 0) "+" else ""
            "Dagens rörelse är $sign${CurrencyHelper.formatDecimal(it)} %. Välj om du vill bevaka upp, ned eller båda håll."
        } ?: "Dagens rörelse saknas just nu. Välj en generell tröskel för större rörelser."
        triggerInfoText.text = "När dagsrörelsen passerar nivån markeras larmet som utlöst och kan återaktiveras senare."
        setPresetChip(presetChipOne, "2 %", 2.0) { thresholdInput.setText(CurrencyHelper.formatDecimal(it)) }
        setPresetChip(presetChipTwo, "3 %", 3.0) { thresholdInput.setText(CurrencyHelper.formatDecimal(it)) }
        setPresetChip(presetChipThree, "5 %", 5.0) { thresholdInput.setText(CurrencyHelper.formatDecimal(it)) }
        setPresetChip(presetChipFour, "8 %", 8.0) { thresholdInput.setText(CurrencyHelper.formatDecimal(it)) }

        MaterialAlertDialogBuilder(context)
            .setTitle("Skapa dagsrörelse-bevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa", null)
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(thresholdInput, selectAll = false)
                dialog.setPositiveActionKeepingOpen(lifecycleScope) {
                    val threshold = when (val input = validatePositiveDecimal(
                        thresholdInput.text?.toString(), "Ange ett tröskelvärde", "Ange ett giltigt tröskelvärde"
                    )) {
                        is DecimalInput.Valid -> input.value
                        is DecimalInput.Invalid -> {
                            thresholdInput.showFieldError(input.message)
                            return@setPositiveActionKeepingOpen false
                        }
                    }
                    val direction = when (directionInput.text.toString()) {
                        "Upp" -> WatchType.DailyMoveDirection.UP
                        "Ned" -> WatchType.DailyMoveDirection.DOWN
                        else -> WatchType.DailyMoveDirection.BOTH
                    }
                    saveNewWatch(
                        WatchType.DailyMove(threshold, direction),
                        errorField = thresholdInput,
                        successMessage = "Dagsrörelse-bevakning skapad"
                    )
                }
            }
    }

    fun showCreateKeyMetricsDialog(
        suggestedMetricType: WatchType.MetricType? = null,
        suggestedTargetValue: Double? = null
    ) {
        val stockData = currentStockData()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_key_metrics, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.metricTypeInput)
        val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.targetValueInput)
        val targetValueLayout = dialogView.findViewById<TextInputLayout>(R.id.targetValueLayout)
        val contextText = dialogView.findViewById<TextView>(R.id.contextText)
        val triggerInfoText = dialogView.findViewById<TextView>(R.id.triggerInfoText)
        val presetChipOne = dialogView.findViewById<Chip>(R.id.presetChipOne)
        val presetChipTwo = dialogView.findViewById<Chip>(R.id.presetChipTwo)
        val presetChipThree = dialogView.findViewById<Chip>(R.id.presetChipThree)
        val presetChipFour = dialogView.findViewById<Chip>(R.id.presetChipFour)

        tickerInput.setText("$symbol - ${currentCompanyName()}", false)
        tickerInput.isEnabled = false

        val historyCard = dialogView.findViewById<CardView>(R.id.historyCard)
        val currentValueText = dialogView.findViewById<TextView>(R.id.currentValueText)
        val historyOneYear = dialogView.findViewById<TextView>(R.id.historyOneYear)
        val historyThreeYear = dialogView.findViewById<TextView>(R.id.historyThreeYear)
        val historyFiveYear = dialogView.findViewById<TextView>(R.id.historyFiveYear)
        historyCard.visibility = View.VISIBLE

	        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent", "Vinst/aktie")
        val metricTypeAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, metricTypes)
        metricTypeInput.setAdapter(metricTypeAdapter)
        metricTypeInput.setText(
            when (suggestedMetricType) {
	                WatchType.MetricType.PE_RATIO -> "P/E-tal"
	                WatchType.MetricType.PS_RATIO -> "P/S-tal"
	                WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
	                WatchType.MetricType.EARNINGS_PER_SHARE -> "Vinst/aktie"
	                null -> ""
            },
            false
        )
        suggestedTargetValue?.let { targetValueInput.setText(CurrencyHelper.formatDecimal(it)) }
        triggerInfoText.text = "När nyckeltalet passerar din nivå markeras larmet som utlöst och kan återaktiveras senare."

        val presetChips = listOf(presetChipOne, presetChipTwo, presetChipThree, presetChipFour)

        fun setMetricValueControlsEnabled(enabled: Boolean) {
            targetValueLayout.isEnabled = enabled
            targetValueInput.isEnabled = enabled
            presetChips.forEach { chip ->
                chip.isEnabled = enabled
            }
        }

        fun selectedMetricType(): WatchType.MetricType? {
            return when (metricTypeInput.text.toString()) {
	                "P/E-tal" -> WatchType.MetricType.PE_RATIO
	                "P/S-tal" -> WatchType.MetricType.PS_RATIO
	                "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
	                "Vinst/aktie" -> WatchType.MetricType.EARNINGS_PER_SHARE
	                else -> null
            }
        }

        fun metricCurrentValue(metricType: WatchType.MetricType): Double? {
            return when (metricType) {
	                WatchType.MetricType.PE_RATIO -> stockData?.peRatio
	                WatchType.MetricType.PS_RATIO -> stockData?.psRatio
	                WatchType.MetricType.DIVIDEND_YIELD -> stockData?.dividendYield
	                WatchType.MetricType.EARNINGS_PER_SHARE -> stockData?.earningsPerShare
	            }
        }

        fun refreshMetricContext(metricType: WatchType.MetricType?) {
            if (metricType == null) {
                setMetricValueControlsEnabled(false)
                targetValueLayout.hint = "Välj nyckeltal först"
                targetValueInput.text?.clear()
                contextText.text = "Välj nyckeltal för att se kontext"
                currentValueText.text = "Aktuellt värde saknas"
                historyOneYear.text = ""
                historyThreeYear.text = ""
                historyFiveYear.text = ""
                presetChips.forEach { chip ->
                    chip.text = "Välj nyckeltal"
                    chip.setOnClickListener(null)
                }
                return
            }

            setMetricValueControlsEnabled(true)
            val currentValue = metricCurrentValue(metricType)
            val summary = currentMetricHistory(metricType)
	            targetValueLayout.hint = when (metricType) {
	                WatchType.MetricType.DIVIDEND_YIELD -> "Målvärde (%)"
	                else -> "Målvärde"
	            }
            contextText.text = buildString {
                append("${metricLabel(metricType)} är ")
                append(currentValue?.let { formatMetricValue(metricType, it) } ?: "okänt")
                summary?.threeYear?.takeUnless { it.isEmpty() }?.average?.let {
                    append(". 3-årssnitt ${formatMetricValue(metricType, it)}")
                }
                append(".")
            }
            currentValueText.text = "Nu: ${currentValue?.let { formatMetricValue(metricType, it) } ?: "-"}"
            historyOneYear.text = summary?.oneYear?.takeUnless { it.isEmpty() }?.let {
                "1 år: ${formatMetricValue(metricType, it.min)} - ${formatMetricValue(metricType, it.max)} | snitt ${formatMetricValue(metricType, it.average)}"
            } ?: "1 år: ingen historik"
            historyThreeYear.text = summary?.threeYear?.takeUnless { it.isEmpty() }?.let {
                "3 år: ${formatMetricValue(metricType, it.min)} - ${formatMetricValue(metricType, it.max)} | snitt ${formatMetricValue(metricType, it.average)}"
            } ?: "3 år: ingen historik"
            historyFiveYear.text = summary?.fiveYear?.takeUnless { it.isEmpty() }?.let {
                "5 år: ${formatMetricValue(metricType, it.min)} - ${formatMetricValue(metricType, it.max)} | snitt ${formatMetricValue(metricType, it.average)}"
            } ?: "5 år: ingen historik"

            val fallbackCurrent = currentValue ?: run {
                presetChips.forEach { chip ->
                    chip.text = "Värde saknas"
                    chip.setOnClickListener(null)
                }
                return
            }
            val multipliers = listOf(0.95, 0.90, 1.05, 1.10)
            val labels = listOf("-5 %", "-10 %", "+5 %", "+10 %")
            presetChips.forEachIndexed { index, chip ->
                val value = fallbackCurrent * multipliers[index]
                setPresetChip(
                    chip,
                    "${labels[index]} (${formatMetricValue(metricType, value)})",
                    value
                ) { targetValueInput.setText(CurrencyHelper.formatDecimal(it)) }
            }
        }
        refreshMetricContext(selectedMetricType())
        metricTypeInput.doAfterTextChanged {
            refreshMetricContext(selectedMetricType())
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Skapa nyckeltalsbevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa", null)
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                if (selectedMetricType() == null) {
                    focusInput(metricTypeInput, selectAll = false)
                    metricTypeInput.post { metricTypeInput.showDropDown() }
                } else {
                    focusInput(targetValueInput, selectAll = false)
                }
                dialog.setPositiveActionKeepingOpen(lifecycleScope) {
                    val metricType = when (metricTypeInput.text.toString()) {
                        "P/E-tal" -> WatchType.MetricType.PE_RATIO
                        "P/S-tal" -> WatchType.MetricType.PS_RATIO
                        "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                        "Vinst/aktie" -> WatchType.MetricType.EARNINGS_PER_SHARE
                        else -> null
                    }
                    if (metricType == null) {
                        metricTypeInput.showFieldError("Välj ett nyckeltal")
                        return@setPositiveActionKeepingOpen false
                    }
                    val targetValue = when (val input = validatePositiveDecimal(
                        targetValueInput.text?.toString(), "Ange ett målvärde", "Ange ett giltigt målvärde"
                    )) {
                        is DecimalInput.Valid -> input.value
                        is DecimalInput.Invalid -> {
                            targetValueInput.showFieldError(input.message)
                            return@setPositiveActionKeepingOpen false
                        }
                    }
                    val data = currentStockData()
                    val currentValue = when (metricType) {
                        WatchType.MetricType.PE_RATIO -> data?.peRatio
                        WatchType.MetricType.PS_RATIO -> data?.psRatio
                        WatchType.MetricType.DIVIDEND_YIELD -> data?.dividendYield
                        WatchType.MetricType.EARNINGS_PER_SHARE -> data?.earningsPerShare
                    } ?: 0.0
                    val direction = if (currentValue > 0.0 && currentValue >= targetValue) {
                        WatchType.PriceDirection.BELOW
                    } else {
                        WatchType.PriceDirection.ABOVE
                    }
                    saveNewWatch(
                        WatchType.KeyMetrics(metricType, targetValue, direction),
                        errorField = targetValueInput,
                        successMessage = "Nyckeltalsbevakning skapad"
                    )
                }
            }
    }

    fun showCreateInsiderBuyDialog() {
        val watchType = WatchType.InsiderBuy()
        MaterialAlertDialogBuilder(context)
            .setTitle("Skapa bevakning för insideraffärer")
            .setMessage("StockFlip kontrollerar nya insideraffärer var 6:e timme.")
            .setPositiveButton("Skapa") { _, _ ->
                lifecycleScope.launch {
                    if (viewModel.isDuplicateWatch(watchType)) {
                        Toast.makeText(context, "En bevakning för insideraffärer finns redan", Toast.LENGTH_SHORT).show()
                    } else if (viewModel.createAlert(watchType, currentCompanyName())) {
                        onWatchChanged()
                        Toast.makeText(context, "Bevakning för insideraffärer skapad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, SAVE_FAILED_MESSAGE, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Kontrollerar dubblett och sparar. Vid fel visas meddelandet vid [errorField] och
     * false returneras så att dialogen ligger kvar; "skapad" bekräftas bara när det sparats.
     */
    private suspend fun saveNewWatch(
        watchType: WatchType,
        errorField: EditText,
        successMessage: String,
        toastDuration: Int = Toast.LENGTH_SHORT
    ): Boolean {
        val isDuplicate = try {
            viewModel.isDuplicateWatch(watchType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorField.showFieldError(SAVE_FAILED_MESSAGE)
            return false
        }
        if (isDuplicate) {
            errorField.showFieldError(DUPLICATE_WATCH_MESSAGE)
            return false
        }
        if (!viewModel.createAlert(watchType, currentCompanyName())) {
            errorField.showFieldError(SAVE_FAILED_MESSAGE)
            return false
        }
        onWatchChanged()
        Toast.makeText(context, successMessage, toastDuration).show()
        return true
    }

    // -------------------------------------------------------------------------
    // Edit-dispatcher och gemensamma dialogs
    // -------------------------------------------------------------------------

    fun showEditWatchItemDialog(item: WatchItem) {
        editor.showEditWatchItemDialog(item)
    }

    fun showDeleteConfirmation(watchItem: WatchItem) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Ta bort bevakning")
            .setMessage("Är du säker på att du vill ta bort denna bevakning?")
            .setPositiveButton("Ta bort") { _, _ ->
                viewModel.deleteAlert(watchItem)
                onWatchChanged()
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
            focusInput(noteInput)
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
