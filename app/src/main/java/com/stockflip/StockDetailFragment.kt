package com.stockflip

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.stockflip.databinding.FragmentStockDetailBinding
import com.stockflip.repository.MetricHistoryRepository
import com.stockflip.MetricHistoryService
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Fragment för att visa detaljer om en enskild aktie.
 * 
 * Enligt PRD Fas 1 ska denna visa:
 * - Namn + ticker
 * - Senaste pris
 * - Dagens förändring i %
 * - 52-veckors högsta
 * - Drawdown % från 52w high
 * - Snabbval för att skapa bevakningar
 * - Lista med befintliga bevakningar
 */
class StockDetailFragment : Fragment() {

    private var _binding: FragmentStockDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: StockDetailViewModel
    private lateinit var alertAdapter: AlertAdapter

    private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))

    companion object {
        private const val TAG = "StockDetailFragment"
        private const val ARG_SYMBOL = "symbol"
        private const val ARG_COMPANY_NAME = "company_name"

        /**
         * Skapar en ny instans av StockDetailFragment.
         */
        fun newInstance(symbol: String, companyName: String? = null): StockDetailFragment {
            return StockDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SYMBOL, symbol)
                    putString(ARG_COMPANY_NAME, companyName)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStockDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val symbol = arguments?.getString(ARG_SYMBOL) ?: run {
            Log.e(TAG, "No symbol provided")
            @Suppress("DEPRECATION")
            requireActivity().onBackPressed()
            return
        }

        val database = StockPairDatabase.getDatabase(requireContext())
        // Skapa ViewModel med Factory eftersom vi har parametrar
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(StockDetailViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return StockDetailViewModel(
                        database.watchItemDao(),
                        YahooFinanceService,
                        symbol
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
        viewModel = ViewModelProvider(this, factory)[StockDetailViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupQuickActions()
        setupObservers()
        
        // Ladda data
        viewModel.loadStockData()
        viewModel.loadAlerts()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        alertAdapter = AlertAdapter(
            onToggleActive = { watchItem ->
                viewModel.toggleAlert(watchItem)
            },
            onReactivate = { watchItem ->
                viewModel.reactivateAlert(watchItem)
            },
            onDelete = { watchItem ->
                showDeleteConfirmation(watchItem)
            },
            onEdit = { watchItem ->
                showEditWatchItemDialog(watchItem)
            }
        )

        binding.alertsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alertAdapter
        }
    }

    private fun setupQuickActions() {
        binding.createPriceTargetButton.setOnClickListener {
            showCreatePriceTargetDialog()
        }

        binding.createDrawdownButton.setOnClickListener {
            showCreateDrawdownDialog()
        }

        binding.createDailyMoveButton.setOnClickListener {
            showCreateDailyMoveDialog()
        }

        binding.createPriceRangeButton.setOnClickListener {
            showCreatePriceRangeDialog()
        }

        binding.createKeyMetricsButton.setOnClickListener {
            showCreateKeyMetricsDialog()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stockDataState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.loadingIndicator.isVisible = true
                    }
                    is UiState.Success -> {
                        binding.loadingIndicator.isVisible = false
                        displayStockData(state.data)
                    }
                    is UiState.Error -> {
                        binding.loadingIndicator.isVisible = false
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.alertsState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        // Loading hanteras av stockDataState
                    }
                    is UiState.Success -> {
                        alertAdapter.submitList(state.data)
                        binding.noAlertsText.isVisible = state.data.isEmpty()
                        binding.alertsRecyclerView.isVisible = state.data.isNotEmpty()
                    }
                    is UiState.Error -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun displayStockData(data: StockDetailData) {
        binding.companyName.text = data.companyName
        binding.symbol.text = data.symbol
        
        binding.lastPrice.text = data.lastPrice?.let { 
            "${priceFormat.format(it)} SEK"
        } ?: "Laddar..."
        
        binding.dailyChangePercent.text = when {
            data.dailyChangePercent != null -> {
                val change = data.dailyChangePercent
                val sign = if (change >= 0) "+" else ""
                val color = if (change >= 0) {
                    android.graphics.Color.parseColor("#4CAF50") // Green
                } else {
                    android.graphics.Color.parseColor("#F44336") // Red
                }
                binding.dailyChangePercent.setTextColor(color)
                "$sign${priceFormat.format(change)}%"
            }
            data.lastPrice != null && data.previousClose != null -> {
                // Vi har både pris och föregående stängning, men förändringen är 0 eller kunde inte beräknas
                binding.dailyChangePercent.setTextColor(android.graphics.Color.parseColor("#757575")) // Gray
                "0.00%"
            }
            data.lastPrice != null -> {
                // Vi har nuvarande pris men ingen föregående stängning (t.ex. helger eller marknaden stängd)
                binding.dailyChangePercent.setTextColor(android.graphics.Color.parseColor("#757575")) // Gray
                "Ej tillgängligt"
            }
            else -> {
                // Inget pris ännu
                binding.dailyChangePercent.setTextColor(android.graphics.Color.parseColor("#757575")) // Gray
                "Laddar..."
            }
        }
        
        binding.week52High.text = data.week52High?.let {
            "${priceFormat.format(it)} SEK"
        } ?: "Laddar..."
        
        binding.drawdownPercent.text = data.drawdownPercent?.let {
            "${priceFormat.format(it)}%"
        } ?: "Laddar..."
    }


    private fun showCreatePriceTargetDialog() {
        val symbol = arguments?.getString(ARG_SYMBOL) ?: return
        val companyName = arguments?.getString(ARG_COMPANY_NAME)
            ?: (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
            ?: ""
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput)
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput)
        
        // Dölj aktie-fältet eftersom vi redan är på denna akties sida
        // TextInputLayout är parent till MaterialAutoCompleteTextView
        tickerInput?.parent?.let { parent ->
            if (parent is com.google.android.material.textfield.TextInputLayout) {
                parent.visibility = android.view.View.GONE
            } else if (parent is android.view.ViewGroup) {
                parent.visibility = android.view.View.GONE
            }
        }
        
        // Förifyll med 10-15% under dagens pris enligt PRD
        viewModel.stockDataState.value?.let { state ->
            if (state is UiState.Success) {
                state.data.lastPrice?.let { currentPrice ->
                    val suggestedPrice = currentPrice * 0.90 // 10% under
                    targetPriceInput.setText(priceFormat.format(suggestedPrice))
                }
            }
        }

        // Set up direction dropdown
        val directions = arrayOf("Över", "Under")
        val directionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Skapa målpris-bevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa") { _, _ ->
                val targetPriceStr = targetPriceInput.text.toString()
                val directionStr = directionInput.text.toString()

                if (targetPriceStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val targetPrice = targetPriceStr.toDoubleOrNull()
                    val direction = when (directionStr) {
                        "Över" -> WatchType.PriceDirection.ABOVE
                        "Under" -> WatchType.PriceDirection.BELOW
                        else -> WatchType.PriceDirection.BELOW
                    }

                    if (targetPrice != null && targetPrice > 0) {
                        val companyName = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
                            ?: arguments?.getString(ARG_COMPANY_NAME) ?: ""
                        
                        viewModel.createAlert(
                            WatchType.PriceTarget(targetPrice, direction),
                            companyName
                        )
                        Toast.makeText(requireContext(), "Målpris-bevakning skapad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ange ett giltigt målpris", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showCreateDrawdownDialog() {
        val symbol = arguments?.getString(ARG_SYMBOL) ?: return
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput)
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput)
        
        // Dölj aktie-fältet eftersom vi redan är på denna akties sida
        // TextInputLayout är parent till MaterialAutoCompleteTextView
        tickerInput?.parent?.let { parent ->
            if (parent is com.google.android.material.textfield.TextInputLayout) {
                parent.visibility = android.view.View.GONE
            } else if (parent is android.view.ViewGroup) {
                parent.visibility = android.view.View.GONE
            }
        }
        
        // Set up drop type dropdown
        val dropTypes = arrayOf("Procent", "Absolut (SEK)")
        val dropTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dropTypes)
        dropTypeInput.setAdapter(dropTypeAdapter)
        dropTypeInput.setText("Procent", false) // Default to percentage
        
        // Visa aktuell drawdown från 52w high enligt PRD
        viewModel.stockDataState.value?.let { state ->
            if (state is UiState.Success) {
                state.data.drawdownPercent?.let { currentDrawdown ->
                    val suggestedDrop = currentDrawdown + 5.0 // 5% mer än nuvarande
                    dropValueInput.setText(priceFormat.format(suggestedDrop))
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Skapa drawdown-bevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa") { _, _ ->
                val dropTypeStr = dropTypeInput.text.toString()
                val dropValueStr = dropValueInput.text.toString()

                if (dropTypeStr.isNotEmpty() && dropValueStr.isNotEmpty()) {
                    val dropType = when (dropTypeStr) {
                        "Procent" -> WatchType.DropType.PERCENTAGE
                        "Absolut (SEK)" -> WatchType.DropType.ABSOLUTE
                        else -> WatchType.DropType.PERCENTAGE
                    }
                    val dropValue = dropValueStr.toDoubleOrNull()

                    if (dropValue != null && dropValue > 0) {
                        val companyName = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
                            ?: arguments?.getString(ARG_COMPANY_NAME) ?: ""
                        
                        viewModel.createAlert(
                            WatchType.ATHBased(dropType, dropValue),
                            companyName
                        )
                        Toast.makeText(requireContext(), "Drawdown-bevakning skapad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ange ett giltigt värde", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showCreateDailyMoveDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_daily_move, null)
        val thresholdInput = dialogView.findViewById<TextInputEditText>(R.id.thresholdInput)
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput)

        // Set up direction dropdown
        val directions = arrayOf("Upp", "Ned", "Båda")
        val directionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Skapa dagsrörelse-bevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa") { _, _ ->
                val thresholdStr = thresholdInput.text.toString()
                val directionStr = directionInput.text.toString()

                if (thresholdStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val threshold = thresholdStr.toDoubleOrNull()
                    val direction = when (directionStr) {
                        "Upp" -> WatchType.DailyMoveDirection.UP
                        "Ned" -> WatchType.DailyMoveDirection.DOWN
                        "Båda" -> WatchType.DailyMoveDirection.BOTH
                        else -> WatchType.DailyMoveDirection.BOTH
                    }

                    if (threshold != null && threshold > 0) {
                        val companyName = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
                            ?: arguments?.getString(ARG_COMPANY_NAME) ?: ""
                        
                        viewModel.createAlert(
                            WatchType.DailyMove(threshold, direction),
                            companyName
                        )
                        Toast.makeText(requireContext(), "Dagsrörelse-bevakning skapad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ange ett giltigt tröskelvärde", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showCreatePriceRangeDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_price_range, null)
        val minPriceInput = dialogView.findViewById<TextInputEditText>(R.id.minPriceInput)
        val maxPriceInput = dialogView.findViewById<TextInputEditText>(R.id.maxPriceInput)
        
        // Förifyll med 10-15% under/över dagens pris enligt PRD
        viewModel.stockDataState.value?.let { state ->
            if (state is UiState.Success) {
                state.data.lastPrice?.let { currentPrice ->
                    val suggestedMinPrice = currentPrice * 0.90 // 10% under
                    val suggestedMaxPrice = currentPrice * 1.10 // 10% över
                    minPriceInput.setText(priceFormat.format(suggestedMinPrice))
                    maxPriceInput.setText(priceFormat.format(suggestedMaxPrice))
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Skapa prisintervall-bevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa") { _, _ ->
                val minPriceStr = minPriceInput.text.toString()
                val maxPriceStr = maxPriceInput.text.toString()

                if (minPriceStr.isNotEmpty() && maxPriceStr.isNotEmpty()) {
                    val minPrice = minPriceStr.toDoubleOrNull()
                    val maxPrice = maxPriceStr.toDoubleOrNull()

                    if (minPrice != null && maxPrice != null && minPrice > 0 && maxPrice > 0) {
                        if (minPrice >= maxPrice) {
                            Toast.makeText(requireContext(), "Minsta pris måste vara mindre än högsta pris", Toast.LENGTH_SHORT).show()
                        } else {
                            val companyName = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
                                ?: arguments?.getString(ARG_COMPANY_NAME) ?: ""
                            
                            viewModel.createAlert(
                                WatchType.PriceRange(minPrice, maxPrice),
                                companyName
                            )
                            Toast.makeText(requireContext(), "Prisintervall-bevakning skapad", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Ange giltiga prisvärden", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showCreateKeyMetricsDialog() {
        val symbol = arguments?.getString(ARG_SYMBOL) ?: return
        val companyName = arguments?.getString(ARG_COMPANY_NAME)
            ?: (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
            ?: ""
        
        // Använd MainActivity's dialog med förvalt symbol
        val mainActivity = requireActivity() as? MainActivity
        if (mainActivity != null) {
            // Använd reflection eller gör metoden internal/public
            // För nu, skapar vi en egen enklare dialog
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_key_metrics, null)
            val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
            val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.metricTypeInput)
            val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.targetValueInput)
            val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput)
            
            // Förifyll aktieslaget
            tickerInput.setText("$symbol - ${companyName ?: symbol}", false)
            tickerInput.isEnabled = false // Gör det read-only eftersom vi redan är på denna akties sida
            
            // History UI elements
            val historyCard = dialogView.findViewById<CardView>(R.id.historyCard)
            val currentValueText = dialogView.findViewById<TextView>(R.id.currentValueText)
            val historyOneYear = dialogView.findViewById<TextView>(R.id.historyOneYear)
            val historyThreeYear = dialogView.findViewById<TextView>(R.id.historyThreeYear)
            val historyFiveYear = dialogView.findViewById<TextView>(R.id.historyFiveYear)
            val presetBelow5YearAvg = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.presetBelow5YearAvg)
            val presetBelow3YearMin = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.presetBelow3YearMin)
            val presetOneYearAvgMinus20 = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.presetOneYearAvgMinus20)

            var selectedMetricType: WatchType.MetricType? = null
            var historySummary: com.stockflip.MetricHistorySummary? = null

            // Set up metric type dropdown
            val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
            val metricTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, metricTypes)
            metricTypeInput.setAdapter(metricTypeAdapter)
            metricTypeInput.setOnItemClickListener { _, _, position, _ ->
                selectedMetricType = when (position) {
                    0 -> WatchType.MetricType.PE_RATIO
                    1 -> WatchType.MetricType.PS_RATIO
                    2 -> WatchType.MetricType.DIVIDEND_YIELD
                    else -> null
                }
                // Load history when metric type is selected
                if (selectedMetricType != null) {
                    loadHistoryForDialog(symbol, selectedMetricType!!, historyCard, currentValueText, historyOneYear, historyThreeYear, historyFiveYear, presetBelow5YearAvg, presetBelow3YearMin, presetOneYearAvgMinus20) { summary ->
                        historySummary = summary
                    }
                }
            }
            
            // Set up preset buttons
            presetBelow5YearAvg.setOnClickListener {
                historySummary?.let { summary ->
                    val value = com.stockflip.ui.presets.MetricPresets.getPresetValue(com.stockflip.ui.presets.PresetType.BELOW_5_YEAR_AVG, summary)
                    value?.let { targetValueInput.setText(String.format(Locale.getDefault(), "%.2f", it)) }
                }
            }
            presetBelow3YearMin.setOnClickListener {
                historySummary?.let { summary ->
                    val value = com.stockflip.ui.presets.MetricPresets.getPresetValue(com.stockflip.ui.presets.PresetType.BELOW_3_YEAR_MIN, summary)
                    value?.let { targetValueInput.setText(String.format(Locale.getDefault(), "%.2f", it)) }
                }
            }
            presetOneYearAvgMinus20.setOnClickListener {
                historySummary?.let { summary ->
                    val value = com.stockflip.ui.presets.MetricPresets.getPresetValue(com.stockflip.ui.presets.PresetType.ONE_YEAR_AVG_MINUS_20, summary)
                    value?.let { targetValueInput.setText(String.format(Locale.getDefault(), "%.2f", it)) }
                }
            }

            // Set up direction dropdown
            val directions = arrayOf("Över", "Under")
            val directionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, directions)
            directionInput.setAdapter(directionAdapter)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Skapa nyckeltalsbevakning")
                .setView(dialogView)
                .setPositiveButton("Skapa") { _, _ ->
                    val metricTypeStr = metricTypeInput.text.toString()
                    val targetValueStr = targetValueInput.text.toString()
                    val directionStr = directionInput.text.toString()

                    if (metricTypeStr.isNotEmpty() && targetValueStr.isNotEmpty() && directionStr.isNotEmpty()) {
                        val metricType = when (metricTypeStr) {
                            "P/E-tal" -> WatchType.MetricType.PE_RATIO
                            "P/S-tal" -> WatchType.MetricType.PS_RATIO
                            "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                            else -> null
                        }
                        val targetValue = targetValueStr.toDoubleOrNull()
                        val direction = when (directionStr) {
                            "Över" -> WatchType.PriceDirection.ABOVE
                            "Under" -> WatchType.PriceDirection.BELOW
                            else -> null
                        }

                        if (metricType != null && targetValue != null && targetValue > 0 && direction != null) {
                            viewModel.createAlert(
                                WatchType.KeyMetrics(metricType, targetValue, direction),
                                companyName
                            )
                            Toast.makeText(requireContext(), "Nyckeltalsbevakning skapad", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Ange giltiga värden för alla fält", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Avbryt", null)
                .show()
        }
    }

    private fun loadHistoryForDialog(
        symbol: String,
        metricType: WatchType.MetricType,
        historyCard: CardView,
        currentValueText: TextView,
        historyOneYear: TextView,
        historyThreeYear: TextView,
        historyFiveYear: TextView,
        presetBelow5YearAvg: com.google.android.material.button.MaterialButton,
        presetBelow3YearMin: com.google.android.material.button.MaterialButton,
        presetOneYearAvgMinus20: com.google.android.material.button.MaterialButton,
        onSummaryLoaded: (com.stockflip.MetricHistorySummary) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Hämta nuvarande värde
                val currentValue = YahooFinanceService.getKeyMetric(symbol, metricType)
                
                val database = StockPairDatabase.getDatabase(requireContext())
                val metricHistoryRepository = MetricHistoryRepository(database.metricHistoryDao())
                val metricHistoryService = MetricHistoryService(metricHistoryRepository)
                
                val summary = metricHistoryService.getOrFetchHistorySummary(symbol, metricType, currentValue)
                
                if (summary != null && !summary.oneYear.isEmpty()) {
                    historyCard.visibility = android.view.View.VISIBLE
                    
                    val metricTypeName = when (metricType) {
                        WatchType.MetricType.PE_RATIO -> "P/E"
                        WatchType.MetricType.PS_RATIO -> "P/S"
                        WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
                    }
                    
                    // Visa nuvarande värde
                    currentValueText.text = "Nuvarande $metricTypeName: ${currentValue?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "N/A"}"
                    
                    if (!summary.oneYear.isEmpty()) {
                        historyOneYear.text = "1 år: snitt ${String.format(Locale.getDefault(), "%.1f", summary.oneYear.average)} (min ${String.format(Locale.getDefault(), "%.1f", summary.oneYear.min)} / max ${String.format(Locale.getDefault(), "%.1f", summary.oneYear.max)})"
                    }
                    if (!summary.threeYear.isEmpty()) {
                        historyThreeYear.text = "3 år: snitt ${String.format(Locale.getDefault(), "%.1f", summary.threeYear.average)} (min ${String.format(Locale.getDefault(), "%.1f", summary.threeYear.min)} / max ${String.format(Locale.getDefault(), "%.1f", summary.threeYear.max)})"
                    }
                    if (!summary.fiveYear.isEmpty()) {
                        historyFiveYear.text = "5 år: snitt ${String.format(Locale.getDefault(), "%.1f", summary.fiveYear.average)} (min ${String.format(Locale.getDefault(), "%.1f", summary.fiveYear.min)} / max ${String.format(Locale.getDefault(), "%.1f", summary.fiveYear.max)})"
                    }
                    
                    onSummaryLoaded(summary)
                } else {
                    historyCard.visibility = android.view.View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading history: ${e.message}", e)
                historyCard.visibility = android.view.View.GONE
            }
        }
    }

    private fun showDeleteConfirmation(watchItem: WatchItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ta bort bevakning")
            .setMessage("Är du säker på att du vill ta bort denna bevakning?")
            .setPositiveButton("Ta bort") { _, _ ->
                viewModel.deleteAlert(watchItem)
                Toast.makeText(requireContext(), "Bevakning borttagen", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showEditWatchItemDialog(item: WatchItem) {
        // Dölj snabbvalen när man redigerar
        binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.quickActionsCard)?.visibility = android.view.View.GONE
        
        when (item.watchType) {
            is WatchType.PriceTarget -> showEditPriceTargetDialog(item)
            is WatchType.PriceRange -> showEditPriceRangeDialog(item)
            is WatchType.DailyMove -> showEditDailyMoveDialog(item)
            is WatchType.ATHBased -> showEditDrawdownDialog(item)
            is WatchType.KeyMetrics -> showEditKeyMetricsDialog(item)
            else -> {
                Toast.makeText(requireContext(), "Redigering för denna bevakningstyp stöds inte ännu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditPriceTargetDialog(item: WatchItem) {
        if (item.watchType !is WatchType.PriceTarget) return
        val priceTarget = item.watchType
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput.parent as? TextInputLayout
        tickerInputLayout?.visibility = android.view.View.GONE
        tickerInput.setText(item.ticker ?: "")
        tickerInput.isEnabled = false
        
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput).apply {
            setText(priceFormat.format(priceTarget.targetPrice))
        }
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput).apply {
            setText(when (priceTarget.direction) {
                WatchType.PriceDirection.ABOVE -> "Över"
                WatchType.PriceDirection.BELOW -> "Under"
            })
        }

        val directions = arrayOf("Över", "Under")
        val directionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Redigera prisbevakning")
            .setView(dialogView)
            .setOnDismissListener {
                // Visa snabbvalen igen när dialogen stängs
                binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.quickActionsCard)?.visibility = android.view.View.VISIBLE
            }
            .setPositiveButton("Uppdatera") { _, _ ->
                val targetPriceStr = targetPriceInput.text.toString()
                val directionStr = directionInput.text.toString()

                if (targetPriceStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val targetPrice = targetPriceStr.toDoubleOrNull()
                    val direction = when (directionStr) {
                        "Över" -> WatchType.PriceDirection.ABOVE
                        "Under" -> WatchType.PriceDirection.BELOW
                        else -> WatchType.PriceDirection.ABOVE
                    }

                    if (targetPrice != null && targetPrice > 0) {
                        val updatedItem = item.copy(
                            watchType = WatchType.PriceTarget(targetPrice, direction)
                        )
                        viewModel.updateWatchItem(updatedItem)
                        Toast.makeText(requireContext(), "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ange ett giltigt målpris", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ ->
                showDeleteConfirmation(item)
            }
            .setNegativeButton("Avbryt", null)
            .create()
        dialog.show()
    }

    private fun showEditPriceRangeDialog(item: WatchItem) {
        if (item.watchType !is WatchType.PriceRange) return
        val priceRange = item.watchType
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_price_range, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput.parent as? TextInputLayout
        tickerInputLayout?.visibility = android.view.View.GONE
        tickerInput.setText(item.ticker ?: "")
        tickerInput.isEnabled = false
        
        val minPriceInput = dialogView.findViewById<TextInputEditText>(R.id.minPriceInput).apply {
            setText(priceFormat.format(priceRange.minPrice))
        }
        val maxPriceInput = dialogView.findViewById<TextInputEditText>(R.id.maxPriceInput).apply {
            setText(priceFormat.format(priceRange.maxPrice))
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Redigera prisintervall-bevakning")
            .setView(dialogView)
            .setOnDismissListener {
                // Visa snabbvalen igen när dialogen stängs
                binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.quickActionsCard)?.visibility = android.view.View.VISIBLE
            }
            .setPositiveButton("Uppdatera") { _, _ ->
                val minPriceStr = minPriceInput.text.toString()
                val maxPriceStr = maxPriceInput.text.toString()

                if (minPriceStr.isNotEmpty() && maxPriceStr.isNotEmpty()) {
                    val minPrice = minPriceStr.toDoubleOrNull()
                    val maxPrice = maxPriceStr.toDoubleOrNull()

                    if (minPrice != null && maxPrice != null && minPrice > 0 && maxPrice > minPrice) {
                        val updatedItem = item.copy(
                            watchType = WatchType.PriceRange(minPrice, maxPrice)
                        )
                        viewModel.updateWatchItem(updatedItem)
                        Toast.makeText(requireContext(), "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ange giltiga prisintervall", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ ->
                showDeleteConfirmation(item)
            }
            .setNegativeButton("Avbryt", null)
            .create()
        dialog.show()
    }

    private fun showEditDailyMoveDialog(item: WatchItem) {
        if (item.watchType !is WatchType.DailyMove) return
        val dailyMove = item.watchType
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_daily_move, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput.parent as? TextInputLayout
        tickerInputLayout?.visibility = android.view.View.GONE
        tickerInput.setText(item.ticker ?: "")
        tickerInput.isEnabled = false
        
        val thresholdInput = dialogView.findViewById<TextInputEditText>(R.id.thresholdInput).apply {
            setText(priceFormat.format(dailyMove.percentThreshold))
        }
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput).apply {
            setText(when (dailyMove.direction) {
                WatchType.DailyMoveDirection.UP -> "Upp"
                WatchType.DailyMoveDirection.DOWN -> "Ned"
                WatchType.DailyMoveDirection.BOTH -> "Båda"
            })
        }

        val directions = arrayOf("Upp", "Ned", "Båda")
        val directionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Redigera dagsrörelse-bevakning")
            .setView(dialogView)
            .setOnDismissListener {
                // Visa snabbvalen igen när dialogen stängs
                binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.quickActionsCard)?.visibility = android.view.View.VISIBLE
            }
            .setPositiveButton("Uppdatera") { _, _ ->
                val thresholdStr = thresholdInput.text.toString()
                val directionStr = directionInput.text.toString()

                if (thresholdStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val threshold = thresholdStr.toDoubleOrNull()
                    val direction = when (directionStr) {
                        "Upp" -> WatchType.DailyMoveDirection.UP
                        "Ned" -> WatchType.DailyMoveDirection.DOWN
                        "Båda" -> WatchType.DailyMoveDirection.BOTH
                        else -> WatchType.DailyMoveDirection.BOTH
                    }

                    if (threshold != null && threshold > 0) {
                        val updatedItem = item.copy(
                            watchType = WatchType.DailyMove(threshold, direction)
                        )
                        viewModel.updateWatchItem(updatedItem)
                        Toast.makeText(requireContext(), "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ange ett giltigt tröskelvärde", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ ->
                showDeleteConfirmation(item)
            }
            .setNegativeButton("Avbryt", null)
            .create()
        dialog.show()
    }

    private fun showEditDrawdownDialog(item: WatchItem) {
        if (item.watchType !is WatchType.ATHBased) return
        val athBased = item.watchType
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput.parent as? TextInputLayout
        tickerInputLayout?.visibility = android.view.View.GONE
        tickerInput.setText(item.ticker ?: "")
        tickerInput.isEnabled = false
        
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput).apply {
            setText(when (athBased.dropType) {
                WatchType.DropType.PERCENTAGE -> "Procent"
                WatchType.DropType.ABSOLUTE -> "Absolut (SEK)"
            })
        }
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput).apply {
            setText(priceFormat.format(athBased.dropValue))
        }

        val dropTypes = arrayOf("Procent", "Absolut (SEK)")
        val dropTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dropTypes)
        dropTypeInput.setAdapter(dropTypeAdapter)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Redigera drawdown-bevakning")
            .setView(dialogView)
            .setOnDismissListener {
                // Visa snabbvalen igen när dialogen stängs
                binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.quickActionsCard)?.visibility = android.view.View.VISIBLE
            }
            .setPositiveButton("Uppdatera") { _, _ ->
                val dropTypeStr = dropTypeInput.text.toString()
                val dropValueStr = dropValueInput.text.toString()

                if (dropTypeStr.isNotEmpty() && dropValueStr.isNotEmpty()) {
                    val dropType = when (dropTypeStr) {
                        "Procent" -> WatchType.DropType.PERCENTAGE
                        "Absolut (SEK)" -> WatchType.DropType.ABSOLUTE
                        else -> WatchType.DropType.PERCENTAGE
                    }
                    val dropValue = dropValueStr.toDoubleOrNull()

                    if (dropValue != null && dropValue > 0) {
                        val updatedItem = item.copy(
                            watchType = WatchType.ATHBased(dropType, dropValue)
                        )
                        viewModel.updateWatchItem(updatedItem)
                        Toast.makeText(requireContext(), "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ange ett giltigt värde", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ ->
                showDeleteConfirmation(item)
            }
            .setNegativeButton("Avbryt", null)
            .create()
        dialog.show()
    }

    private fun showEditKeyMetricsDialog(item: WatchItem) {
        if (item.watchType !is WatchType.KeyMetrics) return
        val keyMetrics = item.watchType
        val symbol = arguments?.getString(ARG_SYMBOL) ?: item.ticker ?: return
        val companyName = arguments?.getString(ARG_COMPANY_NAME)
            ?: (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
            ?: item.companyName
            ?: ""
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_key_metrics, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput.parent as? TextInputLayout
        tickerInputLayout?.visibility = android.view.View.GONE // Dölj ticker-input eftersom vi redan är på rätt aktie
        tickerInput.setText("$symbol - $companyName")
        tickerInput.isEnabled = false
        
        val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.metricTypeInput).apply {
            setText(when (keyMetrics.metricType) {
                WatchType.MetricType.PE_RATIO -> "P/E-tal"
                WatchType.MetricType.PS_RATIO -> "P/S-tal"
                WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
            })
        }
        val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.targetValueInput).apply {
            setText(priceFormat.format(keyMetrics.targetValue))
        }
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput).apply {
            setText(when (keyMetrics.direction) {
                WatchType.PriceDirection.ABOVE -> "Över"
                WatchType.PriceDirection.BELOW -> "Under"
            })
        }
        
        // History UI elements
        val historyCard = dialogView.findViewById<CardView>(R.id.historyCard)
        val currentValueText = dialogView.findViewById<TextView>(R.id.currentValueText)
        val historyOneYear = dialogView.findViewById<TextView>(R.id.historyOneYear)
        val historyThreeYear = dialogView.findViewById<TextView>(R.id.historyThreeYear)
        val historyFiveYear = dialogView.findViewById<TextView>(R.id.historyFiveYear)
        val presetBelow5YearAvg = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.presetBelow5YearAvg)
        val presetBelow3YearMin = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.presetBelow3YearMin)
        val presetOneYearAvgMinus20 = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.presetOneYearAvgMinus20)

        var historySummary: com.stockflip.MetricHistorySummary? = null

        // Ladda historik automatiskt när dialogen öppnas
        loadHistoryForDialog(symbol, keyMetrics.metricType, historyCard, currentValueText, historyOneYear, historyThreeYear, historyFiveYear, presetBelow5YearAvg, presetBelow3YearMin, presetOneYearAvgMinus20) { summary ->
            historySummary = summary
        }

        // Set up metric type dropdown
        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        val metricTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, metricTypes)
        metricTypeInput.setAdapter(metricTypeAdapter)
        metricTypeInput.setOnItemClickListener { _, _, position, _ ->
            val newMetricType = when (position) {
                0 -> WatchType.MetricType.PE_RATIO
                1 -> WatchType.MetricType.PS_RATIO
                2 -> WatchType.MetricType.DIVIDEND_YIELD
                else -> keyMetrics.metricType
            }
            // Ladda historik när metric type ändras
            loadHistoryForDialog(symbol, newMetricType, historyCard, currentValueText, historyOneYear, historyThreeYear, historyFiveYear, presetBelow5YearAvg, presetBelow3YearMin, presetOneYearAvgMinus20) { summary ->
                historySummary = summary
            }
        }
        
        // Set up preset buttons
        presetBelow5YearAvg.setOnClickListener {
            historySummary?.let { summary ->
                val value = com.stockflip.ui.presets.MetricPresets.getPresetValue(com.stockflip.ui.presets.PresetType.BELOW_5_YEAR_AVG, summary)
                value?.let { targetValueInput.setText(String.format(Locale.getDefault(), "%.2f", it)) }
            }
        }
        presetBelow3YearMin.setOnClickListener {
            historySummary?.let { summary ->
                val value = com.stockflip.ui.presets.MetricPresets.getPresetValue(com.stockflip.ui.presets.PresetType.BELOW_3_YEAR_MIN, summary)
                value?.let { targetValueInput.setText(String.format(Locale.getDefault(), "%.2f", it)) }
            }
        }
        presetOneYearAvgMinus20.setOnClickListener {
            historySummary?.let { summary ->
                val value = com.stockflip.ui.presets.MetricPresets.getPresetValue(com.stockflip.ui.presets.PresetType.ONE_YEAR_AVG_MINUS_20, summary)
                value?.let { targetValueInput.setText(String.format(Locale.getDefault(), "%.2f", it)) }
            }
        }

        // Set up direction dropdown
        val directions = arrayOf("Över", "Under")
        val directionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Redigera nyckeltalsbevakning")
            .setView(dialogView)
            .setOnDismissListener {
                // Visa snabbvalen igen när dialogen stängs
                binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.quickActionsCard)?.visibility = android.view.View.VISIBLE
            }
            .setPositiveButton("Uppdatera") { _, _ ->
                val metricTypeStr = metricTypeInput.text.toString()
                val targetValueStr = targetValueInput.text.toString()
                val directionStr = directionInput.text.toString()

                if (metricTypeStr.isNotEmpty() && targetValueStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val metricType = when (metricTypeStr) {
                        "P/E-tal" -> WatchType.MetricType.PE_RATIO
                        "P/S-tal" -> WatchType.MetricType.PS_RATIO
                        "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                        else -> null
                    }
                    val targetValue = targetValueStr.toDoubleOrNull()
                    val direction = when (directionStr) {
                        "Över" -> WatchType.PriceDirection.ABOVE
                        "Under" -> WatchType.PriceDirection.BELOW
                        else -> null
                    }

                    if (metricType != null && targetValue != null && targetValue > 0 && direction != null) {
                        val updatedItem = item.copy(
                            watchType = WatchType.KeyMetrics(metricType, targetValue, direction)
                        )
                        viewModel.updateWatchItem(updatedItem)
                        Toast.makeText(requireContext(), "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ange giltiga värden", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ta bort") { _, _ ->
                showDeleteConfirmation(item)
            }
            .setNegativeButton("Avbryt", null)
            .create()
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


