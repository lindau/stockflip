package com.stockflip.ui.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
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
import com.stockflip.R
import com.stockflip.StockSearchResult
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.parseDecimal
import com.stockflip.ui.builders.ConditionBuilderAdapter
import com.stockflip.viewmodel.StockSearchViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WatchItemEditor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val stockSearchViewModel: StockSearchViewModel,
    private val stockSearchViewModel2: StockSearchViewModel,
    private val allowSymbolEditing: Boolean,
    private val createStockAdapter: () -> ArrayAdapter<StockSearchResult>,
    private val setupStockSearch: (MaterialAutoCompleteTextView, ArrayAdapter<StockSearchResult>, StockSearchViewModel, Boolean) -> Unit,
    private val onUpdateWatchItem: suspend (WatchItem) -> Unit,
    private val onDeleteRequested: (WatchItem) -> Unit,
    private val onBeforeDialog: (() -> Unit)? = null,
    private val onDialogDismissed: (() -> Unit)? = null,
    private val currentCurrencyFor: (WatchItem) -> String = { CurrencyHelper.getCurrencyFromSymbol(it.ticker ?: "") },
    private val currentPriceFor: (WatchItem) -> Double? = { null },
    private val currentMetricValueFor: (WatchItem) -> Double? = { null }
) {
    fun showEditWatchItemDialog(item: WatchItem) {
        onBeforeDialog?.invoke()
        when (item.watchType.kind) {
            WatchType.Kind.PRICE_PAIR -> showEditPricePairDialog(item)
            WatchType.Kind.PRICE_TARGET -> showEditPriceTargetDialog(item)
            WatchType.Kind.KEY_METRICS -> showEditKeyMetricsDialog(item)
            WatchType.Kind.ATH_BASED -> showEditDrawdownDialog(item)
            WatchType.Kind.PRICE_RANGE -> showEditPriceRangeDialog(item)
            WatchType.Kind.DAILY_MOVE -> showEditDailyMoveDialog(item)
            WatchType.Kind.COMBINED -> showEditCombinedAlertDialog(item)
        }
    }

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
        setupStockSearch(ticker1Input, adapter1, stockSearchViewModel, false)
        setupStockSearch(ticker2Input, adapter2, stockSearchViewModel2, false)

        var selectedStock1: StockSearchResult? = null
        var selectedStock2: StockSearchResult? = null

        ticker1Input.setOnItemClickListener { _, _, position, _ -> selectedStock1 = adapter1.getItem(position) }
        ticker2Input.setOnItemClickListener { _, _, position, _ -> selectedStock2 = adapter2.getItem(position) }

        MaterialAlertDialogBuilder(context)
            .setTitle("Redigera aktiepar")
            .setView(dialogView)
            .setOnDismissListener { onDialogDismissed?.invoke() }
            .setPositiveButton("Uppdatera") { _, _ ->
                val ticker1Str = ticker1Input.text.toString().trim()
                val ticker2Str = ticker2Input.text.toString().trim()
                val priceDifferenceStr = priceDifferenceInput.text.toString()
                val notifyWhenEqual = notifyWhenEqualCheckbox.isChecked
                val finalTicker1 = selectedStock1?.symbol ?: ticker1Str
                val finalTicker2 = selectedStock2?.symbol ?: ticker2Str
                if (finalTicker1.isNotEmpty() && finalTicker2.isNotEmpty()) {
                    val priceDifference = priceDifferenceStr.parseDecimal() ?: 0.0
                    val updatedItem = item.copy(
                        watchType = WatchType.PricePair(priceDifference, notifyWhenEqual),
                        ticker1 = finalTicker1,
                        ticker2 = finalTicker2,
                        companyName1 = selectedStock1?.name ?: item.companyName1,
                        companyName2 = selectedStock2?.name ?: item.companyName2
                    )
                    runUpdate(updatedItem, "Aktiepar uppdaterat", "Kunde inte uppdatera aktiepar")
                } else {
                    Toast.makeText(context, "Välj båda aktier", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ -> onDeleteRequested(item) }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(priceDifferenceInput)
            }
    }

    private fun showEditPriceTargetDialog(item: WatchItem) {
        if (item.watchType !is WatchType.PriceTarget) return
        val priceTarget = item.watchType
        val currencySymbol = CurrencyHelper.getCurrencySymbol(currentCurrencyFor(item))
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput).apply { setText(item.ticker) }
        val tickerInputLayout = tickerInput.parent as? TextInputLayout
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput).apply {
            setText(CurrencyHelper.formatDecimal(priceTarget.targetPrice))
        }
        dialogView.findViewById<TextInputLayout>(R.id.targetPriceLayout)?.hint = "Målpris ($currencySymbol)"

        var selectedStock: StockSearchResult? = null
        if (allowSymbolEditing) {
            val adapter = createStockAdapter()
            tickerInput.setAdapter(adapter)
            setupStockSearch(tickerInput, adapter, stockSearchViewModel, true)
            tickerInput.setOnItemClickListener { _, _, position, _ -> selectedStock = adapter.getItem(position) }
        } else {
            tickerInputLayout?.visibility = View.GONE
            tickerInput.isEnabled = false
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Redigera prisbevakning")
            .setView(dialogView)
            .setOnDismissListener { onDialogDismissed?.invoke() }
            .setPositiveButton("Uppdatera") { _, _ ->
                val finalTicker = selectedStock?.symbol ?: tickerInput.text.toString().trim()
                val targetPrice = targetPriceInput.text.toString().parseDecimal()
                if (finalTicker.isNotEmpty() && targetPrice != null && targetPrice > 0) {
                    val existingDirection = priceTarget.direction
                    val direction = currentPriceFor(item)?.let { price ->
                        if (price >= targetPrice) WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
                    } ?: existingDirection
                    val updatedItem = item.copy(
                        watchType = WatchType.PriceTarget(targetPrice, direction),
                        ticker = finalTicker,
                        companyName = selectedStock?.name ?: item.companyName
                    )
                    runUpdate(updatedItem, "Prisbevakning uppdaterad", "Kunde inte uppdatera prisbevakning")
                } else {
                    Toast.makeText(context, "Ange ett giltigt målpris", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ -> onDeleteRequested(item) }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(targetPriceInput)
            }
    }

    private fun showEditKeyMetricsDialog(item: WatchItem) {
        if (item.watchType !is WatchType.KeyMetrics) return
        val keyMetrics = item.watchType
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_key_metrics, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput).apply { setText(item.ticker) }
        val tickerInputLayout = tickerInput.parent as? TextInputLayout
        val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.metricTypeInput).apply {
            setText(
                when (keyMetrics.metricType) {
                    WatchType.MetricType.PE_RATIO -> "P/E-tal"
                    WatchType.MetricType.PS_RATIO -> "P/S-tal"
                    WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
                },
                false
            )
        }
        val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.targetValueInput).apply {
            setText(CurrencyHelper.formatDecimal(keyMetrics.targetValue))
        }
        dialogView.findViewById<CardView>(R.id.historyCard)?.visibility = View.GONE

        var selectedStock: StockSearchResult? = null
        if (allowSymbolEditing) {
            val adapter = createStockAdapter()
            tickerInput.setAdapter(adapter)
            setupStockSearch(tickerInput, adapter, stockSearchViewModel, false)
            tickerInput.setOnItemClickListener { _, _, position, _ -> selectedStock = adapter.getItem(position) }
        } else {
            tickerInputLayout?.visibility = View.GONE
            tickerInput.isEnabled = false
        }

        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        metricTypeInput.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, metricTypes))

        MaterialAlertDialogBuilder(context)
            .setTitle("Redigera nyckeltalsbevakning")
            .setView(dialogView)
            .setOnDismissListener { onDialogDismissed?.invoke() }
            .setPositiveButton("Uppdatera") { _, _ ->
                val finalTicker = selectedStock?.symbol ?: tickerInput.text.toString().trim()
                val targetValue = targetValueInput.text.toString().parseDecimal()
                val metricType = when (metricTypeInput.text.toString()) {
                    "P/E-tal" -> WatchType.MetricType.PE_RATIO
                    "P/S-tal" -> WatchType.MetricType.PS_RATIO
                    "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                    else -> null
                }
                if (finalTicker.isNotEmpty() && metricType != null && targetValue != null && targetValue > 0) {
                    val existingDirection = keyMetrics.direction
                    val direction = currentMetricValueFor(item)?.let { value ->
                        if (value >= targetValue) WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
                    } ?: existingDirection
                    val updatedItem = item.copy(
                        watchType = WatchType.KeyMetrics(metricType, targetValue, direction),
                        ticker = finalTicker,
                        companyName = selectedStock?.name ?: item.companyName
                    )
                    runUpdate(updatedItem, "Nyckeltalsbevakning uppdaterad", "Kunde inte uppdatera nyckeltalsbevakning")
                } else {
                    Toast.makeText(context, "Ange giltiga värden för alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ -> onDeleteRequested(item) }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(targetValueInput)
            }
    }

    private fun showEditDrawdownDialog(item: WatchItem) {
        if (item.watchType !is WatchType.ATHBased) return
        val athBased = item.watchType
        val currencySymbol = CurrencyHelper.getCurrencySymbol(currentCurrencyFor(item))
        val absoluteLabel = "Absolut ($currencySymbol)"
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput).apply { setText(item.ticker) }
        val tickerInputLayout = tickerInput.parent as? TextInputLayout
        val highReferenceInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.highReferenceInput)
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput).apply {
            setText(
                when (athBased.dropType) {
                    WatchType.DropType.PERCENTAGE -> "Procent"
                    WatchType.DropType.ABSOLUTE -> absoluteLabel
                },
                false
            )
        }
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput).apply {
            setText(CurrencyHelper.formatDecimal(athBased.dropValue))
        }
        dialogView.findViewById<View>(R.id.presetChipGroup)?.visibility = View.GONE
        dialogView.findViewById<TextView>(R.id.contextText)?.text =
            "Välj om drawdown ska räknas från 52v högsta eller historiskt högsta pris."

        fun referenceLabel(reference: WatchType.HighReference): String = when (reference) {
            WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> "52v högsta"
            WatchType.HighReference.ALL_TIME_HIGH -> "Historiskt högsta"
        }

        fun selectedReference(): WatchType.HighReference = when (highReferenceInput.text.toString()) {
            "Historiskt högsta" -> WatchType.HighReference.ALL_TIME_HIGH
            else -> WatchType.HighReference.FIFTY_TWO_WEEK_HIGH
        }

        var selectedStock: StockSearchResult? = null
        if (allowSymbolEditing) {
            val adapter = createStockAdapter()
            tickerInput.setAdapter(adapter)
            setupStockSearch(tickerInput, adapter, stockSearchViewModel, true)
            tickerInput.setOnItemClickListener { _, _, position, _ -> selectedStock = adapter.getItem(position) }
        } else {
            tickerInputLayout?.visibility = View.GONE
            tickerInput.isEnabled = false
        }

        val dropTypes = arrayOf("Procent", absoluteLabel)
        dropTypeInput.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, dropTypes))
        val references = arrayOf(referenceLabel(WatchType.HighReference.FIFTY_TWO_WEEK_HIGH), referenceLabel(WatchType.HighReference.ALL_TIME_HIGH))
        highReferenceInput.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, references))
        highReferenceInput.setText(referenceLabel(athBased.reference), false)

        MaterialAlertDialogBuilder(context)
            .setTitle("Redigera drawdown-bevakning")
            .setView(dialogView)
            .setOnDismissListener { onDialogDismissed?.invoke() }
            .setPositiveButton("Uppdatera") { _, _ ->
                val finalTicker = selectedStock?.symbol ?: tickerInput.text.toString().trim()
                val dropType = when (dropTypeInput.text.toString()) {
                    "Procent" -> WatchType.DropType.PERCENTAGE
                    else -> WatchType.DropType.ABSOLUTE
                }
                val dropValue = dropValueInput.text.toString().parseDecimal()
                if (finalTicker.isNotEmpty() && dropValue != null && dropValue > 0) {
                    val updatedItem = item.copy(
                        watchType = WatchType.ATHBased(dropType, dropValue, selectedReference()),
                        ticker = finalTicker,
                        companyName = selectedStock?.name ?: item.companyName
                    )
                    runUpdate(updatedItem, "Bevakning uppdaterad", "Kunde inte uppdatera drawdown-bevakning")
                } else {
                    Toast.makeText(context, "Ange ett giltigt värde", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ -> onDeleteRequested(item) }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(dropValueInput)
            }
    }

    private fun showEditPriceRangeDialog(item: WatchItem) {
        showEditPriceRangeDialog(
            context = context,
            item = item,
            currency = currentCurrencyFor(item),
            onUpdate = { minPrice, maxPrice ->
                val updatedItem = item.copy(watchType = WatchType.PriceRange(minPrice, maxPrice))
                runUpdate(updatedItem, context.getString(R.string.toast_price_range_updated), context.getString(R.string.toast_watch_update_failed, ""))
            },
            onDelete = { onDeleteRequested(item) },
            onDismiss = onDialogDismissed
        )
    }

    private fun showEditDailyMoveDialog(item: WatchItem) {
        if (item.watchType !is WatchType.DailyMove) return
        val dailyMove = item.watchType
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_daily_move, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView?>(R.id.tickerInput)
        val tickerInputLayout = tickerInput?.parent as? TextInputLayout
        tickerInputLayout?.visibility = View.GONE

        val thresholdInput = dialogView.findViewById<TextInputEditText>(R.id.thresholdInput).apply {
            setText(CurrencyHelper.formatDecimal(dailyMove.percentThreshold))
        }
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput).apply {
            setText(
                when (dailyMove.direction) {
                    WatchType.DailyMoveDirection.UP -> "Upp"
                    WatchType.DailyMoveDirection.DOWN -> "Ned"
                    WatchType.DailyMoveDirection.BOTH -> "Båda"
                },
                false
            )
        }
        val directions = arrayOf("Upp", "Ned", "Båda")
        directionInput.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, directions))

        MaterialAlertDialogBuilder(context)
            .setTitle("Redigera dagsrörelse-bevakning")
            .setView(dialogView)
            .setOnDismissListener { onDialogDismissed?.invoke() }
            .setPositiveButton("Uppdatera") { _, _ ->
                val threshold = thresholdInput.text.toString().parseDecimal()
                val direction = when (directionInput.text.toString()) {
                    "Upp" -> WatchType.DailyMoveDirection.UP
                    "Ned" -> WatchType.DailyMoveDirection.DOWN
                    else -> WatchType.DailyMoveDirection.BOTH
                }
                if (threshold != null && threshold > 0) {
                    val updatedItem = item.copy(watchType = WatchType.DailyMove(threshold, direction))
                    runUpdate(updatedItem, "Dagsrörelse-bevakning uppdaterad", "Kunde inte uppdatera dagsrörelse-bevakning")
                } else {
                    Toast.makeText(context, "Ange ett giltigt tröskelvärde", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ -> onDeleteRequested(item) }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(thresholdInput)
            }
    }

    private fun showEditCombinedAlertDialog(watchItem: WatchItem) {
        val combined = watchItem.watchType as? WatchType.Combined ?: return
        val decompositionResult = decomposeExpression(combined.expression)
        if (decompositionResult == null) {
            onDialogDismissed?.invoke()
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
            onConditionTypeChanged = { _, _ -> updatePreview(conditionAdapter, symbolInput.text.toString(), previewText) },
            onReferenceChanged = { _, _ -> updatePreview(conditionAdapter, symbolInput.text.toString(), previewText) },
            onValueChanged = { _, _ -> updatePreview(conditionAdapter, symbolInput.text.toString(), previewText) },
            onOperatorChanged = { _, _ -> updatePreview(conditionAdapter, symbolInput.text.toString(), previewText) },
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
        setupStockSearch(symbolInput, stockAdapter, stockSearchViewModel, true)
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
            .setOnDismissListener { onDialogDismissed?.invoke() }
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

                val updatedItem = watchItem.copy(
                    watchType = WatchType.Combined(newExpression),
                    ticker = newSymbol
                )
                runUpdate(updatedItem, "Kombinerat larm uppdaterat", "Kunde inte uppdatera kombinerat larm")
            }
            .setNeutralButton("Ta bort") { _, _ -> onDeleteRequested(watchItem) }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun runUpdate(updatedItem: WatchItem, successMessage: String, errorMessage: String) {
        scope.launch {
            try {
                onUpdateWatchItem(updatedItem)
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val suffix = e.message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                Toast.makeText(context, errorMessage + suffix, Toast.LENGTH_LONG).show()
            }
        }
    }

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
                            conditionType = "Drawdown",
                            highReference = rule.reference,
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

        conditions[0].operator = null
        return Pair(currentSymbol, conditions.toList())
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
            "Drawdown" -> AlertRule.SingleDrawdownFromHigh(
                currentSymbol,
                AlertRule.DrawdownDropType.PERCENTAGE,
                value,
                condition.highReference
            )
            "Dagsrörelse" -> AlertRule.SingleDailyMove(currentSymbol, value, AlertRule.DailyMoveDirection.BOTH)
            else -> null
        }
    }
}
