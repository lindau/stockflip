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
                        val watchType = WatchType.PriceTarget(targetPrice, direction)
                        lifecycleScope.launch {
                            if (viewModel.isDuplicateWatch(watchType)) {
                                Toast.makeText(context, "En bevakning med dessa inställningar finns redan", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.createAlert(watchType, currentCompanyName())
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
                        val watchType = WatchType.ATHBased(dropType, dropValue)
                        lifecycleScope.launch {
                            if (viewModel.isDuplicateWatch(watchType)) {
                                Toast.makeText(context, "En bevakning med dessa inställningar finns redan", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.createAlert(watchType, currentCompanyName())
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
                        val watchType = WatchType.DailyMove(threshold, direction)
                        lifecycleScope.launch {
                            if (viewModel.isDuplicateWatch(watchType)) {
                                Toast.makeText(context, "En bevakning med dessa inställningar finns redan", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.createAlert(watchType, currentCompanyName())
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
                        val watchType = WatchType.KeyMetrics(metricType, targetValue, WatchType.PriceDirection.ABOVE)
                        lifecycleScope.launch {
                            if (viewModel.isDuplicateWatch(watchType)) {
                                Toast.makeText(context, "En bevakning med dessa inställningar finns redan", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.createAlert(watchType, currentCompanyName())
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
