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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.stockflip.ui.SwipeToDeleteCallback
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.stockflip.databinding.FragmentStockDetailBinding
import com.stockflip.repository.MetricHistoryRepository
import com.stockflip.MetricHistoryService
import com.stockflip.viewmodel.StockSearchViewModel
import com.stockflip.repository.StockRepository
import com.stockflip.repository.SearchState
import com.stockflip.ui.builders.ConditionBuilderAdapter
import android.widget.ImageView
import android.text.TextWatcher
import android.text.Editable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Fragment för att visa detaljer om en enskild aktie/krypto.
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
    private lateinit var stockSearchViewModel: StockSearchViewModel

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

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refresh()
        }
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
        
        // Setup stock search ViewModel
        val searchFactory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(StockSearchViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return StockSearchViewModel(StockRepository()) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
        stockSearchViewModel = ViewModelProvider(this, searchFactory)[StockSearchViewModel::class.java]

        setupRecyclerView()
        setupQuickActions()
        setupObservers()
        setupSwipeRefresh()

        // Ladda data
        viewModel.loadStockData()
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

        val swipeCallback = SwipeToDeleteCallback(
            context = requireContext(),
            onSwiped = { position ->
                val item = alertAdapter.currentList.getOrNull(position) ?: return@SwipeToDeleteCallback
                alertAdapter.notifyItemChanged(position)
                viewModel.deleteAlert(item)
                Toast.makeText(requireContext(), "Bevakning borttagen", Toast.LENGTH_SHORT).show()
            }
        )
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.alertsRecyclerView)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadStockData()
        }
    }

    private fun setupQuickActions() {
        val companyName = arguments?.getString(ARG_COMPANY_NAME)
        val symbol = arguments?.getString(ARG_SYMBOL)
        binding.addWatchHeaderText.text = "Lägg till bevakning av ${companyName ?: symbol ?: ""}"

        binding.createPriceTargetButton.setOnClickListener {
            showCreatePriceTargetDialog()
        }

        binding.createDrawdownButton.setOnClickListener {
            showCreateDrawdownDialog()
        }

        binding.createDailyMoveButton.setOnClickListener {
            showCreateDailyMoveDialog()
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
                        binding.swipeRefreshLayout.isRefreshing = false
                        displayStockData(state.data)
                    }
                    is UiState.Error -> {
                        binding.loadingIndicator.isVisible = false
                        binding.swipeRefreshLayout.isRefreshing = false
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
        
        // Visa landsflagga baserat på börs och valuta (valuta som fallback)
        // För krypto returneras null, då visas ingen flagga
        val flagEmoji = CountryFlagHelper.getFlagForExchange(data.exchange, data.currency)
        binding.countryFlag.text = flagEmoji ?: ""
        
        // Dölj nyckeltal-knappen om det är en kryptovaluta
        val isCrypto = StockSearchResult.isCryptoSymbol(data.symbol)
        binding.createKeyMetricsButton.visibility = if (isCrypto) android.view.View.GONE else android.view.View.VISIBLE
        
        binding.lastPrice.text = data.lastPrice?.let { 
            CurrencyHelper.formatPrice(it, data.currency)
        } ?: "Laddar..."
        
        // Kontrollera om börsen är öppen baserat på börs-kod
        // Om exchange är null, försök gissa från symbol (t.ex. .ST för svenska, .OL för norska)
        val exchangeToCheck = data.exchange ?: run {
            when {
                data.symbol.endsWith(".ST") || data.symbol.endsWith(".STO") -> "STO"
                data.symbol.endsWith(".OL") || data.symbol.endsWith(".OSE") -> "OSE"
                data.symbol.endsWith(".L") -> "LSE"
                data.symbol.endsWith(".DE") || data.symbol.endsWith(".XETR") -> "XETR"
                data.symbol.endsWith(".T") -> "TSE"
                // För amerikanska aktier utan suffix, anta NASDAQ/NYSE
                !data.symbol.contains(".") && data.currency == "USD" -> "NASDAQ"
                else -> null
            }
        }
        
        val isMarketOpen = if (isCrypto) {
            true // Krypto är alltid öppet
        } else {
            val marketOpen = StockMarketScheduler.isMarketOpenForExchange(exchangeToCheck)
            Log.d(TAG, "Market status for ${data.symbol} (exchange: ${data.exchange}, checked: $exchangeToCheck, currency: ${data.currency}): $marketOpen")
            marketOpen
        }
        
        val canShowDailyChange: Boolean = data.dailyChangePercent != null ||
            (data.lastPrice != null && data.previousClose != null && data.previousClose > 0)
        if (canShowDailyChange) {
            binding.dailyChangeRow.visibility = android.view.View.VISIBLE
            val change: Double = data.dailyChangePercent ?: run {
                val lp = requireNotNull(data.lastPrice)
                val pc = requireNotNull(data.previousClose)
                ((lp - pc) / pc) * 100
            }
            if (!isMarketOpen) {
                binding.dailyChangePercent.setTextColor(android.graphics.Color.parseColor("#757575")) // Gray
                binding.dailyChangePercent.text = "Börsen stängd"
            } else {
                val sign = if (change >= 0) "+" else ""
                val color = if (change >= 0) {
                    android.graphics.Color.parseColor("#4CAF50") // Green
                } else {
                    android.graphics.Color.parseColor("#F44336") // Red
                }
                binding.dailyChangePercent.setTextColor(color)
                binding.dailyChangePercent.text = "$sign${priceFormat.format(change)}%"
            }
        } else {
            binding.dailyChangeRow.visibility = android.view.View.GONE
        }
        
        binding.week52High.text = data.week52High?.let {
            CurrencyHelper.formatPrice(it, data.currency)
        } ?: "Laddar..."
        
        binding.week52Low.text = data.week52Low?.let {
            CurrencyHelper.formatPrice(it, data.currency)
        } ?: "Laddar..."
        
        binding.drawdownPercent.text = data.drawdownPercent?.let {
            "${priceFormat.format(it)}%"
        } ?: "Laddar..."
    }


    private fun showCreatePriceTargetDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput)

        // Dölj aktie/krypto-fältet eftersom vi redan är på denna akties/kryptos sida
        // TextInputLayout är parent till MaterialAutoCompleteTextView
        tickerInput?.parent?.let { parent ->
            if (parent is com.google.android.material.textfield.TextInputLayout) {
                parent.visibility = android.view.View.GONE
            } else if (parent is android.view.ViewGroup) {
                parent.visibility = android.view.View.GONE
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Skapa målpris-bevakning")
            .setView(dialogView)
            .setPositiveButton("Skapa") { _, _ ->
                val targetPriceStr = targetPriceInput.text.toString()

                if (targetPriceStr.isNotEmpty()) {
                    val targetPrice = targetPriceStr.toDoubleOrNull()

                    if (targetPrice != null && targetPrice > 0) {
                        val currentPrice = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.lastPrice ?: 0.0
                        val direction = if (currentPrice > 0.0 && currentPrice >= targetPrice)
                            WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
                        val currentCompanyName = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
                            ?: arguments?.getString(ARG_COMPANY_NAME) ?: ""

                        viewModel.createAlert(
                            WatchType.PriceTarget(targetPrice, direction),
                            currentCompanyName
                        )
                        Toast.makeText(requireContext(), "Målpris-bevakning skapad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ange ett giltigt målpris", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Ange ett målpris", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showCreateDrawdownDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput)
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput)
        
        // Dölj aktie/krypto-fältet eftersom vi redan är på denna akties/kryptos sida
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
                        val currentCompanyName = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
                            ?: arguments?.getString(ARG_COMPANY_NAME) ?: ""
                        
                        viewModel.createAlert(
                            WatchType.ATHBased(dropType, dropValue),
                            currentCompanyName
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
        val symbol = arguments?.getString(ARG_SYMBOL) ?: return
        val companyName = arguments?.getString(ARG_COMPANY_NAME)
            ?: (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
            ?: ""
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_daily_move, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val tickerInputLayout = tickerInput?.parent as? TextInputLayout
        tickerInputLayout?.visibility = android.view.View.GONE
        tickerInput?.setText("$symbol - $companyName")
        tickerInput?.isEnabled = false
        
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
                        val currentCompanyName = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
                            ?: arguments?.getString(ARG_COMPANY_NAME) ?: ""
                        
                        viewModel.createAlert(
                            WatchType.DailyMove(threshold, direction),
                            currentCompanyName
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
                            val currentCompanyName = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
                                ?: arguments?.getString(ARG_COMPANY_NAME) ?: ""
                            
                            viewModel.createAlert(
                                WatchType.PriceRange(minPrice, maxPrice),
                                currentCompanyName
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

            // Förifyll aktie/krypto-slaget
            tickerInput.setText("$symbol - $companyName", false)
            tickerInput.isEnabled = false // Gör det read-only eftersom vi redan är på denna akties/kryptos sida

            // History UI elements - hidden
            val historyCard = dialogView.findViewById<CardView>(R.id.historyCard)
            historyCard.visibility = android.view.View.GONE

            // Set up metric type dropdown
            val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
            val metricTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, metricTypes)
            metricTypeInput.setAdapter(metricTypeAdapter)

            MaterialAlertDialogBuilder(requireContext())
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
                        val targetValue = targetValueStr.toDoubleOrNull()

                        if (metricType != null && targetValue != null && targetValue > 0) {
                            viewModel.createAlert(
                                WatchType.KeyMetrics(metricType, targetValue, WatchType.PriceDirection.ABOVE),
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
            is WatchType.Combined -> showEditCombinedAlertDialog(item)
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

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Redigera prisbevakning")
            .setView(dialogView)
            .setOnDismissListener {
                // Visa snabbvalen igen när dialogen stängs
                binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.quickActionsCard)?.visibility = android.view.View.VISIBLE
            }
            .setPositiveButton("Uppdatera") { _, _ ->
                val targetPriceStr = targetPriceInput.text.toString()

                if (targetPriceStr.isNotEmpty()) {
                    val targetPrice = targetPriceStr.toDoubleOrNull()

                    if (targetPrice != null && targetPrice > 0) {
                        val direction = if (item.currentPrice > 0.0 && item.currentPrice >= targetPrice)
                            WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
                        val updatedItem = item.copy(
                            watchType = WatchType.PriceTarget(targetPrice, direction)
                        )
                        viewModel.updateWatchItem(updatedItem)
                        Toast.makeText(requireContext(), "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ange ett giltigt målpris", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Ange ett målpris", Toast.LENGTH_SHORT).show()
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
        val tickerInputLayout = tickerInput?.parent as? TextInputLayout
        tickerInputLayout?.visibility = android.view.View.GONE
        tickerInput?.setText(item.ticker ?: "")
        tickerInput?.isEnabled = false
        
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
        tickerInputLayout?.visibility = android.view.View.GONE // Dölj ticker-input eftersom vi redan är på rätt aktie/krypto
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

        // History UI elements - hidden
        val historyCard = dialogView.findViewById<CardView>(R.id.historyCard)
        historyCard.visibility = android.view.View.GONE

        // Set up metric type dropdown
        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        val metricTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, metricTypes)
        metricTypeInput.setAdapter(metricTypeAdapter)
        metricTypeInput.setOnItemClickListener { _, _, _, _ ->
            // Metric type changed - no history loading
        }

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

                if (metricTypeStr.isNotEmpty() && targetValueStr.isNotEmpty()) {
                    val metricType = when (metricTypeStr) {
                        "P/E-tal" -> WatchType.MetricType.PE_RATIO
                        "P/S-tal" -> WatchType.MetricType.PS_RATIO
                        "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                        else -> null
                    }
                    val targetValue = targetValueStr.toDoubleOrNull()

                    if (metricType != null && targetValue != null && targetValue > 0) {
                        val direction = if (item.currentMetricValue > 0.0 && item.currentMetricValue >= targetValue)
                            WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
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

    /**
     * Shows a dialog for editing an existing combined alert.
     */
    private fun showEditCombinedAlertDialog(watchItem: WatchItem) {
        val combined = watchItem.watchType as? WatchType.Combined ?: return
        val expression = combined.expression
        
        // Dekomponera uttrycket till villkor
        val decompositionResult = decomposeExpression(expression)
        if (decompositionResult == null) {
            Toast.makeText(requireContext(), "Detta kombinerat larm kan inte redigeras (komplex struktur)", Toast.LENGTH_LONG).show()
            return
        }
        
        val (symbol, conditions) = decompositionResult
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_combined_alert, null)
        val symbolInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.symbolInput)
        val conditionsRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.conditionsRecyclerView)
        val addConditionButton = dialogView.findViewById<MaterialButton>(R.id.addConditionButton)
        val previewText = dialogView.findViewById<TextView>(R.id.previewText)

        // Setup RecyclerView
        conditionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // Create condition adapter with existing conditions
        lateinit var conditionAdapter: ConditionBuilderAdapter
        
        conditionAdapter = ConditionBuilderAdapter(
            onConditionTypeChanged = { _, _ ->
                val newSymbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, newSymbol, previewText)
            },
            onValueChanged = { _, _ ->
                val newSymbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, newSymbol, previewText)
            },
            onOperatorChanged = { _, _ ->
                val newSymbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, newSymbol, previewText)
            },
            onRemove = { position ->
                conditionAdapter.removeCondition(position)
                val newSymbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, newSymbol, previewText)
            }
        )
        
        // Lägg till befintliga villkor med värden
        conditionAdapter.setConditions(conditions)
        
        conditionsRecyclerView.adapter = conditionAdapter

        // Setup stock adapter for symbol input
        val stockAdapter = createStockAdapter()
        symbolInput.setAdapter(stockAdapter)
        symbolInput.setText(symbol, false)
        
        // Set up search functionality
        setupStockSearch(symbolInput, stockAdapter, stockSearchViewModel, includeCrypto = true)
        
        symbolInput.setOnItemClickListener { _, _, itemPosition, _ ->
            val item = stockAdapter.getItem(itemPosition)
            val newSymbol = item?.symbol ?: symbolInput.text.toString()
            updatePreview(conditionAdapter, newSymbol, previewText)
        }

        // Add condition button
        addConditionButton.setOnClickListener {
            conditionAdapter.addCondition()
            val newSymbol = symbolInput.text.toString()
            updatePreview(conditionAdapter, newSymbol, previewText)
        }

        // Initial preview
        updatePreview(conditionAdapter, symbol, previewText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Redigera kombinerat larm")
            .setView(dialogView)
            .setPositiveButton("Spara") { _, _ ->
                val newSymbol = symbolInput.text.toString().trim()
                val newConditions = conditionAdapter.getConditions()
                
                // Validate symbol
                if (newSymbol.isEmpty()) {
                    Toast.makeText(requireContext(), "Välj en aktie", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (newConditions.isEmpty()) {
                    Toast.makeText(requireContext(), "Lägg till minst ett villkor", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Validate all conditions
                val validConditions = newConditions.filter { 
                    it.value.isNotEmpty() && 
                    it.value.toDoubleOrNull() != null 
                }
                
                if (validConditions.size != newConditions.size) {
                    Toast.makeText(requireContext(), "Alla villkor måste ha giltigt värde", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Build AlertExpression
                val newExpression = buildAlertExpression(newSymbol, validConditions)
                if (newExpression == null) {
                    Toast.makeText(requireContext(), "Kunde inte skapa uttryck", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                lifecycleScope.launch {
                    try {
                        val updatedWatchItem = watchItem.copy(
                            watchType = WatchType.Combined(newExpression),
                            ticker = newSymbol
                        )
                        
                        viewModel.updateWatchItem(updatedWatchItem)
                        Toast.makeText(requireContext(), "Kombinerat larm uppdaterat", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Kunde inte uppdatera kombinerat larm: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNeutralButton("Ta bort") { _, _ ->
                showDeleteConfirmation(watchItem)
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Dekomponerar en AlertExpression till symbol och lista av villkor.
     * Fungerar bara för "flat" uttryck (alla AND eller alla OR, inga parenteser).
     */
    private fun decomposeExpression(expression: AlertExpression): Pair<String, List<ConditionBuilderAdapter.ConditionData>>? {
        val conditions = mutableListOf<ConditionBuilderAdapter.ConditionData>()
        var currentSymbol: String? = null
        
        fun extractRules(expr: AlertExpression, operator: String? = null): Boolean {
            return when (expr) {
                is AlertExpression.Single -> {
                    val rule = expr.rule
                    val symbol = when (rule) {
                        is AlertRule.SinglePrice -> rule.symbol
                        is AlertRule.SingleDrawdownFromHigh -> rule.symbol
                        is AlertRule.SingleDailyMove -> rule.symbol
                        is AlertRule.SingleKeyMetric -> rule.symbol
                        is AlertRule.PairSpread -> return false // PairSpread stöds inte
                    }
                    
                    // Kontrollera att alla villkor använder samma aktie
                    if (currentSymbol == null) {
                        currentSymbol = symbol
                    } else if (currentSymbol != symbol) {
                        return false // Olika aktier, kan inte dekomponeras
                    }
                    
                    // Konvertera AlertRule till ConditionData
                    val conditionData = when (rule) {
                        is AlertRule.SinglePrice -> {
                            ConditionBuilderAdapter.ConditionData(
                                conditionType = "Pris",
                                direction = if (rule.comparisonType == AlertRule.PriceComparisonType.ABOVE) "Över" else "Under",
                                value = rule.priceLimit.toString(),
                                operator = operator
                            )
                        }
                        is AlertRule.SingleDrawdownFromHigh -> {
                            ConditionBuilderAdapter.ConditionData(
                                conditionType = "52w High Drop",
                                direction = "Över",
                                value = rule.dropValue.toString(),
                                operator = operator
                            )
                        }
                        is AlertRule.SingleDailyMove -> {
                            ConditionBuilderAdapter.ConditionData(
                                conditionType = "Dagsrörelse",
                                direction = "Över",
                                value = rule.percentThreshold.toString(),
                                operator = operator
                            )
                        }
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
                    val rightOk = extractRules(expr.right, "OCH")
                    leftOk && rightOk
                }
                is AlertExpression.Or -> {
                    val leftOk = extractRules(expr.left, null)
                    if (!leftOk) return false
                    val rightOk = extractRules(expr.right, "ELLER")
                    leftOk && rightOk
                }
                is AlertExpression.Not -> {
                    false // NOT stöds inte för redigering
                }
            }
        }
        
        val success = extractRules(expression)
        if (!success || currentSymbol == null || conditions.isEmpty()) {
            return null
        }
        
        // Ta bort operator från första villkoret
        if (conditions.isNotEmpty()) {
            conditions[0].operator = null
        }
        
        return Pair(currentSymbol!!, conditions.toList())
    }

    /**
     * Updates the preview text showing the current expression.
     */
    private fun updatePreview(
        adapter: ConditionBuilderAdapter,
        symbol: String,
        previewText: TextView
    ) {
        val conditions = adapter.getConditions()
        if (symbol.isEmpty()) {
            previewText.text = "Välj en aktie"
            previewText.setTextColor(requireContext().getColor(android.R.color.darker_gray))
            return
        }
        
        if (conditions.isEmpty()) {
            previewText.text = "Lägg till minst ett villkor"
            previewText.setTextColor(requireContext().getColor(android.R.color.darker_gray))
            return
        }
        
        val expression = buildAlertExpression(symbol, conditions)
        if (expression != null) {
            previewText.text = expression.getDescription()
            previewText.setTextColor(requireContext().getColor(android.R.color.black))
        } else {
            previewText.text = "Ofullständiga villkor"
            previewText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
        }
    }

    /**
     * Builds an AlertExpression from a list of conditions with operators between them.
     */
    private fun buildAlertExpression(
        symbol: String,
        conditions: List<ConditionBuilderAdapter.ConditionData>
    ): AlertExpression? {
        if (conditions.isEmpty()) return null
        
        // Convert conditions to AlertRules
        val rules = conditions.mapNotNull { condition ->
            buildAlertRule(symbol, condition)
        }
        
        if (rules.isEmpty()) return null
        
        // Start with first rule
        var expression: AlertExpression = AlertExpression.Single(rules.first())
        
        // Combine with remaining rules using their operators
        for (i in 1 until rules.size) {
            val nextExpression = AlertExpression.Single(rules[i])
            val operator = conditions[i].operator ?: "OCH"
            val isAnd = operator.contains("OCH")
            
            expression = if (isAnd) {
                AlertExpression.And(expression, nextExpression)
            } else {
                AlertExpression.Or(expression, nextExpression)
            }
        }
        
        return expression
    }

    /**
     * Builds an AlertRule from a ConditionData and symbol.
     */
    private fun buildAlertRule(symbol: String, condition: ConditionBuilderAdapter.ConditionData): AlertRule? {
        val value = condition.value.toDoubleOrNull() ?: return null
        
        return when (condition.conditionType) {
            "Pris" -> {
                val comparisonType = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SinglePrice(symbol, comparisonType, value)
            }
            "P/E-tal" -> {
                val direction = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SingleKeyMetric(symbol, AlertRule.KeyMetricType.PE_RATIO, value, direction)
            }
            "P/S-tal" -> {
                val direction = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SingleKeyMetric(symbol, AlertRule.KeyMetricType.PS_RATIO, value, direction)
            }
            "Utdelningsprocent" -> {
                val direction = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SingleKeyMetric(symbol, AlertRule.KeyMetricType.DIVIDEND_YIELD, value, direction)
            }
            "52w High Drop" -> {
                AlertRule.SingleDrawdownFromHigh(symbol, AlertRule.DrawdownDropType.PERCENTAGE, value)
            }
            "Dagsrörelse" -> {
                AlertRule.SingleDailyMove(symbol, value, AlertRule.DailyMoveDirection.BOTH)
            }
            else -> null
        }
    }

    /**
     * Creates an adapter for displaying stock search results.
     */
    private fun createStockAdapter(): ArrayAdapter<StockSearchResult> {
        return object : ArrayAdapter<StockSearchResult>(
            requireContext(),
            R.layout.dropdown_item_with_icon,
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return createAdapterItemView(position, convertView, parent)
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return createAdapterItemView(position, convertView, parent)
            }

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
                    @Suppress("UNCHECKED_CAST")
                    override fun performFiltering(constraint: CharSequence?): android.widget.Filter.FilterResults {
                        val filterResults = android.widget.Filter.FilterResults()
                        filterResults.values = mutableListOf<StockSearchResult>()
                        filterResults.count = 0
                        return filterResults
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: android.widget.Filter.FilterResults?) {
                        // Do nothing - we handle filtering through the ViewModel
                    }
                }
            }
        }
    }

    /**
     * Sets up the stock search functionality for an input field.
     */
    private fun setupStockSearch(
        input: MaterialAutoCompleteTextView,
        adapter: ArrayAdapter<StockSearchResult>,
        viewModel: StockSearchViewModel,
        includeCrypto: Boolean = true
    ) {
        input.threshold = 2
        input.setAdapter(adapter)
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchState.collect { state ->
                when (state) {
                    is SearchState.Loading -> {
                        // Loading state
                    }
                    is SearchState.Success -> {
                        adapter.clear()
                        adapter.addAll(state.results)
                        adapter.notifyDataSetChanged()
                        
                        if (state.results.isNotEmpty() && input.text.isNotEmpty()) {
                            input.post {
                                if (input.hasFocus()) {
                                    input.showDropDown()
                                }
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
            
            textChangeJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(300)
                viewModel.search(text.toString(), includeCrypto)
            }
        }

        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && input.text.isNotEmpty() && adapter.count > 0) {
                input.post { input.showDropDown() }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


