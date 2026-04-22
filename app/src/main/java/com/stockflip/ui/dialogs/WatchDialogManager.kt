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
                label = "-10 % (${CurrencyHelper.formatPrice(price * 0.90, currentCurrency())})",
                value = price * 0.90
            ) { targetPriceInput.setText(CurrencyHelper.formatDecimal(it)) }
            setPresetChip(
                chip = presetChipTwo,
                label = "-15 % (${CurrencyHelper.formatPrice(price * 0.85, currentCurrency())})",
                value = price * 0.85
            ) { targetPriceInput.setText(CurrencyHelper.formatDecimal(it)) }
            setPresetChip(
                chip = presetChipThree,
                label = "-20 % (${CurrencyHelper.formatPrice(price * 0.80, currentCurrency())})",
                value = price * 0.80
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
        suggestedDropValue: Double? = null
    ) {
        val currencySymbol = currentCurrencySymbol()
        val stockData = currentStockData()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput)
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput)
        val dropValueLayout = dialogView.findViewById<TextInputLayout>(R.id.dropValueLayout)
        val contextText = dialogView.findViewById<TextView>(R.id.contextText)
        val triggerInfoText = dialogView.findViewById<TextView>(R.id.triggerInfoText)
        val presetChipOne = dialogView.findViewById<Chip>(R.id.presetChipOne)
        val presetChipTwo = dialogView.findViewById<Chip>(R.id.presetChipTwo)
        val presetChipThree = dialogView.findViewById<Chip>(R.id.presetChipThree)

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
        dropTypeInput.setText(
            if (suggestedDropType == WatchType.DropType.PERCENTAGE) "Procent" else "Absolut ($currencySymbol)",
            false
        )
        suggestedDropValue?.let { dropValueInput.setText(CurrencyHelper.formatDecimal(it)) }
        contextText.text = buildString {
            append("Aktuell drawdown ")
            append(stockData?.drawdownPercent?.let { "${CurrencyHelper.formatDecimal(it)}%" } ?: "saknas")
            stockData?.week52High?.let {
                append(" från 52v högsta ${CurrencyHelper.formatPrice(it, currentCurrency())}")
            }
            append(".")
        }
        triggerInfoText.text = "När nedgångsnivån nås markeras larmet som utlöst och kan återaktiveras senare."

        fun applyDrawdownPresets(dropType: WatchType.DropType) {
            if (dropType == WatchType.DropType.PERCENTAGE) {
                dropValueLayout.hint = "Nedgångsvärde (%)"
                setPresetChip(presetChipOne, "10 %", 10.0) {
                    dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                }
                setPresetChip(presetChipTwo, "15 %", 15.0) {
                    dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                }
                setPresetChip(presetChipThree, "20 %", 20.0) {
                    dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                }
            } else {
                dropValueLayout.hint = "Nedgångsvärde ($currencySymbol)"
                val high = stockData?.week52High
                if (high != null && high > 0.0) {
                    val values = listOf(high * 0.10, high * 0.15, high * 0.20)
                    setPresetChip(presetChipOne, CurrencyHelper.formatPrice(values[0], currentCurrency()), values[0]) {
                        dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                    }
                    setPresetChip(presetChipTwo, CurrencyHelper.formatPrice(values[1], currentCurrency()), values[1]) {
                        dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                    }
                    setPresetChip(presetChipThree, CurrencyHelper.formatPrice(values[2], currentCurrency()), values[2]) {
                        dropValueInput.setText(CurrencyHelper.formatDecimal(it))
                    }
                }
            }
        }
        applyDrawdownPresets(suggestedDropType)
        dropTypeInput.doAfterTextChanged {
            val selectedType = if (it.toString() == "Procent") WatchType.DropType.PERCENTAGE else WatchType.DropType.ABSOLUTE
            applyDrawdownPresets(selectedType)
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
                    val dropValue = dropValueStr.parseDecimal()
                    if (dropValue != null && dropValue > 0) {
                        val watchType = WatchType.ATHBased(dropType, dropValue)
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
        setPresetChip(presetChipOne, "3 %", 3.0) { thresholdInput.setText(CurrencyHelper.formatDecimal(it)) }
        setPresetChip(presetChipTwo, "5 %", 5.0) { thresholdInput.setText(CurrencyHelper.formatDecimal(it)) }
        setPresetChip(presetChipThree, "8 %", 8.0) { thresholdInput.setText(CurrencyHelper.formatDecimal(it)) }

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

        tickerInput.setText("$symbol - ${currentCompanyName()}", false)
        tickerInput.isEnabled = false

        val historyCard = dialogView.findViewById<CardView>(R.id.historyCard)
        val currentValueText = dialogView.findViewById<TextView>(R.id.currentValueText)
        val historyOneYear = dialogView.findViewById<TextView>(R.id.historyOneYear)
        val historyThreeYear = dialogView.findViewById<TextView>(R.id.historyThreeYear)
        val historyFiveYear = dialogView.findViewById<TextView>(R.id.historyFiveYear)
        historyCard.visibility = View.VISIBLE

        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        val metricTypeAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, metricTypes)
        metricTypeInput.setAdapter(metricTypeAdapter)
        metricTypeInput.setText(
            when (suggestedMetricType) {
                WatchType.MetricType.PE_RATIO -> "P/E-tal"
                WatchType.MetricType.PS_RATIO -> "P/S-tal"
                WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
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
                else -> null
            }
        }

        fun metricCurrentValue(metricType: WatchType.MetricType): Double? {
            return when (metricType) {
                WatchType.MetricType.PE_RATIO -> stockData?.peRatio
                WatchType.MetricType.PS_RATIO -> stockData?.psRatio
                WatchType.MetricType.DIVIDEND_YIELD -> stockData?.dividendYield
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

            val average = summary?.threeYear?.takeUnless { it.isEmpty() }?.average
            val fallbackCurrent = currentValue ?: return
            if (average != null && average > 0.0) {
                setPresetChip(
                    presetChipOne,
                    "3-årssnitt (${formatMetricValue(metricType, average)})",
                    average
                ) { targetValueInput.setText(CurrencyHelper.formatDecimal(it)) }
            } else {
                presetChipOne.text = "3-årssnitt saknas"
                presetChipOne.setOnClickListener(null)
            }

            val secondValue = if (metricType == WatchType.MetricType.DIVIDEND_YIELD) fallbackCurrent * 1.10 else fallbackCurrent * 0.90
            val thirdValue = if (metricType == WatchType.MetricType.DIVIDEND_YIELD) fallbackCurrent * 1.20 else fallbackCurrent * 0.80
            val secondLabel = if (metricType == WatchType.MetricType.DIVIDEND_YIELD) "+10 %" else "-10 %"
            val thirdLabel = if (metricType == WatchType.MetricType.DIVIDEND_YIELD) "+20 %" else "-20 %"
            setPresetChip(
                presetChipTwo,
                "$secondLabel (${formatMetricValue(metricType, secondValue)})",
                secondValue
            ) { targetValueInput.setText(CurrencyHelper.formatDecimal(it)) }
            setPresetChip(
                presetChipThree,
                "$thirdLabel (${formatMetricValue(metricType, thirdValue)})",
                thirdValue
            ) { targetValueInput.setText(CurrencyHelper.formatDecimal(it)) }
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
                        else -> null
                    }
                    val targetValue = targetValueStr.parseDecimal()
                    if (metricType != null && targetValue != null && targetValue > 0) {
                        val currentValue = when (metricType) {
                            WatchType.MetricType.PE_RATIO -> (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.peRatio
                            WatchType.MetricType.PS_RATIO -> (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.psRatio
                            WatchType.MetricType.DIVIDEND_YIELD -> (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.dividendYield
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
