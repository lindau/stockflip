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
                viewModel.updateWatchItem(updatedItem)
                onWatchChanged()
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
        triggerInfoText.text = "När målpriset nås markeras larmet som utlöst och kan återaktiveras senare."

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
            .setPositiveButton("Skapa") { _, _ ->
                val targetPriceStr = targetPriceInput.text.toString()
                if (targetPriceStr.isNotEmpty()) {
                    val targetPrice = targetPriceStr.parseDecimal()
                    if (targetPrice != null && targetPrice > 0) {
                        val latestPrice = currentStockData()?.lastPrice ?: 0.0
                        val direction = if (latestPrice > 0.0 && latestPrice >= targetPrice)
                            WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
                        val watchType = WatchType.PriceTarget(targetPrice, direction)
                        lifecycleScope.launch {
                            if (viewModel.isDuplicateWatch(watchType)) {
                                Toast.makeText(context, "En bevakning med dessa inställningar finns redan", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.createAlert(watchType, currentCompanyName())
                                onWatchChanged()
                                Toast.makeText(context, "Målpris-bevakning skapad", Toast.LENGTH_SHORT).show()
                            }
                        }
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
                focusInput(targetPriceInput, selectAll = false)
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
            .setPositiveButton("Skapa") { _, _ ->
                val dropTypeStr = dropTypeInput.text.toString()
                val dropValueStr = dropValueInput.text.toString()
                if (dropTypeStr.isNotEmpty() && dropValueStr.isNotEmpty()) {
                    val dropType = when (dropTypeStr) {
                        "Procent" -> WatchType.DropType.PERCENTAGE
                        else -> WatchType.DropType.ABSOLUTE
                    }
                    val reference = selectedReference()
                    val dropValue = dropValueStr.parseDecimal()
                    if (dropValue != null && dropValue > 0) {
                        val watchType = WatchType.ATHBased(dropType, dropValue, reference)
                        lifecycleScope.launch {
                            if (viewModel.isDuplicateWatch(watchType)) {
                                Toast.makeText(context, "En bevakning med dessa inställningar finns redan", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.createAlert(watchType, currentCompanyName())
                                onWatchChanged()
                                Toast.makeText(context, "Drawdown-bevakning skapad", Toast.LENGTH_SHORT).show()
                            }
                        }
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
                focusInput(dropValueInput, selectAll = false)
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
                        val watchType = WatchType.DailyMove(threshold, direction)
                        lifecycleScope.launch {
                            if (viewModel.isDuplicateWatch(watchType)) {
                                Toast.makeText(context, "En bevakning med dessa inställningar finns redan", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.createAlert(watchType, currentCompanyName())
                                onWatchChanged()
                                Toast.makeText(context, "Dagsrörelse-bevakning skapad", Toast.LENGTH_SHORT).show()
                            }
                        }
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
                focusInput(thresholdInput, selectAll = false)
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
                contextText.text = "Välj nyckeltal för att se kontext"
                currentValueText.text = "Aktuellt värde saknas"
                historyOneYear.text = ""
                historyThreeYear.text = ""
                historyFiveYear.text = ""
                return
            }

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
                listOf(presetChipOne, presetChipTwo, presetChipThree, presetChipFour).forEach { chip ->
                    chip.text = "Värde saknas"
                    chip.setOnClickListener(null)
                }
                return
            }
            val multipliers = listOf(0.95, 0.90, 1.05, 1.10)
            val labels = listOf("-5 %", "-10 %", "+5 %", "+10 %")
            val chips = listOf(presetChipOne, presetChipTwo, presetChipThree, presetChipFour)
            chips.forEachIndexed { index, chip ->
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
            .setPositiveButton("Skapa") { _, _ ->
                val metricTypeStr = metricTypeInput.text.toString()
                val targetValueStr = targetValueInput.text.toString()
                if (metricTypeStr.isNotEmpty() && targetValueStr.isNotEmpty()) {
                    val metricType = when (metricTypeStr) {
	                        "P/E-tal" -> WatchType.MetricType.PE_RATIO
	                        "P/S-tal" -> WatchType.MetricType.PS_RATIO
	                        "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
	                        "Vinst/aktie" -> WatchType.MetricType.EARNINGS_PER_SHARE
	                        else -> null
                    }
                    val targetValue = targetValueStr.parseDecimal()
                    if (metricType != null && targetValue != null && targetValue > 0) {
                        val currentValue = when (metricType) {
	                            WatchType.MetricType.PE_RATIO -> (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.peRatio
	                            WatchType.MetricType.PS_RATIO -> (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.psRatio
	                            WatchType.MetricType.DIVIDEND_YIELD -> (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.dividendYield
	                            WatchType.MetricType.EARNINGS_PER_SHARE -> (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.earningsPerShare
	                        } ?: 0.0
                        val direction = if (currentValue > 0.0 && currentValue >= targetValue) {
                            WatchType.PriceDirection.BELOW
                        } else {
                            WatchType.PriceDirection.ABOVE
                        }
                        val watchType = WatchType.KeyMetrics(metricType, targetValue, direction)
                        lifecycleScope.launch {
                            if (viewModel.isDuplicateWatch(watchType)) {
                                Toast.makeText(context, "En bevakning med dessa inställningar finns redan", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.createAlert(watchType, currentCompanyName())
                                onWatchChanged()
                                Toast.makeText(context, "Nyckeltalsbevakning skapad", Toast.LENGTH_SHORT).show()
                            }
                        }
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
                focusInput(targetValueInput, selectAll = false)
            }
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
