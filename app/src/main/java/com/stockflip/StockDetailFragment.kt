package com.stockflip

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.stockflip.ui.SwipeToDeleteCallback
import com.stockflip.databinding.FragmentStockDetailBinding
import com.stockflip.repository.TriggerHistoryRepository
import com.stockflip.viewmodel.StockSearchViewModel
import com.stockflip.repository.StockRepository
import com.stockflip.ui.dialogs.WatchDialogManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.stockflip.ui.components.IntradayChart
import com.stockflip.ui.theme.StockFlipTheme

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
    private lateinit var stockSearchViewModel2: StockSearchViewModel
    private lateinit var dialogManager: WatchDialogManager

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
                        symbol,
                        TriggerHistoryRepository(database.triggerHistoryDao()),
                        database.stockNoteDao()
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

        val searchFactory2 = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(StockSearchViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return StockSearchViewModel(StockRepository()) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
        stockSearchViewModel2 = ViewModelProvider(this, searchFactory2)[StockSearchViewModel::class.java]

        dialogManager = WatchDialogManager(
            fragment = this,
            viewModel = viewModel,
            stockSearchViewModel = stockSearchViewModel,
            stockSearchViewModel2 = stockSearchViewModel2,
            symbol = symbol,
            companyName = arguments?.getString(ARG_COMPANY_NAME)
        )

        setupRecyclerView()
        setupQuickActions()
        setupObservers()
        setupSwipeRefresh()

        binding.notesCard.setOnClickListener { dialogManager.showEditNoteDialog() }

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
                dialogManager.showDeleteConfirmation(watchItem)
            },
            onEdit = { watchItem ->
                dialogManager.showEditWatchItemDialog(watchItem)
            },
            useVariantBackground = true
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
                viewModel.deleteAlert(item.item)
                Toast.makeText(requireContext(), "Bevakning borttagen", Toast.LENGTH_SHORT).show()
            }
        )
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.alertsRecyclerView)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun setupQuickActions() {
        val companyName = arguments?.getString(ARG_COMPANY_NAME)
        val symbol = arguments?.getString(ARG_SYMBOL)
        binding.addWatchHeaderText.text = "Lägg till bevakning av ${companyName ?: symbol ?: ""}"

        configureQuickActionButton(binding.createPriceTargetButton, WatchType.Kind.PRICE_TARGET) {
            dialogManager.showCreatePriceTargetDialog()
        }

        configureQuickActionButton(binding.createDrawdownButton, WatchType.Kind.ATH_BASED) {
            dialogManager.showCreateDrawdownDialog()
        }

        configureQuickActionButton(binding.createDailyMoveButton, WatchType.Kind.DAILY_MOVE) {
            dialogManager.showCreateDailyMoveDialog()
        }

        configureQuickActionButton(binding.createKeyMetricsButton, WatchType.Kind.KEY_METRICS) {
            dialogManager.showCreateKeyMetricsDialog()
        }
    }

    private fun configureQuickActionButton(
        button: View,
        watchKind: WatchType.Kind,
        onClick: () -> Unit
    ) {
        button.isVisible = watchKind.supportsStockDetailQuickCreate
        if (watchKind.supportsStockDetailQuickCreate) {
            button.setOnClickListener { onClick() }
        } else {
            button.setOnClickListener(null)
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.triggerHistoryState.collect { history ->
                alertAdapter.updateTriggerHistory(history)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.chartState, viewModel.selectedPeriod) { state, period -> state to period }
                .collect { (state, period) ->
                    when (state) {
                        is UiState.Loading -> { /* behåll synligheten under periodobyte */ }
                        is UiState.Success -> {
                            binding.intradayChartView.isVisible = true
                            binding.intradayChartView.setContent {
                                StockFlipTheme {
                                    IntradayChart(
                                        data = state.data,
                                        selectedPeriod = period,
                                        onPeriodSelected = { viewModel.selectPeriod(it) }
                                    )
                                }
                            }
                        }
                        is UiState.Error -> binding.intradayChartView.isVisible = false
                    }
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.noteState.collect { note ->
                if (note != null && note.note.isNotBlank()) {
                    binding.notesText.text = note.note
                    binding.notesText.setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            binding.notesText,
                            com.google.android.material.R.attr.colorOnSurface
                        )
                    )
                } else {
                    binding.notesText.setText(R.string.notes_placeholder)
                    binding.notesText.setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            binding.notesText,
                            com.google.android.material.R.attr.colorOnSurfaceVariant
                        )
                    )
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
            binding.dailyChangePercent.visibility = android.view.View.VISIBLE
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
                binding.dailyChangePercent.text = "$sign${CurrencyHelper.formatDecimal(change)}%"
            }
        } else {
            binding.dailyChangePercent.visibility = android.view.View.GONE
        }
        
        binding.week52High.text = data.week52High?.let {
            CurrencyHelper.formatPrice(it, data.currency)
        } ?: "Laddar..."
        
        binding.week52Low.text = data.week52Low?.let {
            CurrencyHelper.formatPrice(it, data.currency)
        } ?: "Laddar..."
        
        binding.drawdownPercent.text = data.drawdownPercent?.let {
            "${CurrencyHelper.formatDecimal(it)}%"
        } ?: "Laddar..."
        val dropValue = data.drawdownPercent ?: 0.0
        val dropColor = if (dropValue > 0)
            android.graphics.Color.parseColor("#F44336")
        else
            com.google.android.material.color.MaterialColors.getColor(
                binding.drawdownPercent,
                com.google.android.material.R.attr.colorOnSurface
            )
        binding.drawdownPercent.setTextColor(dropColor)

        // Nyckeltal-rad (dold för krypto och om inga värden finns)
        val hasAnyMetric = data.peRatio != null || data.psRatio != null || data.dividendYield != null
        binding.keyMetricsRow.visibility = if (hasAnyMetric) android.view.View.VISIBLE else android.view.View.GONE
        binding.peRatioValue.text = data.peRatio?.let { CurrencyHelper.formatDecimal(it) } ?: "-"
        binding.psRatioValue.text = data.psRatio?.let { CurrencyHelper.formatDecimal(it) } ?: "-"
        binding.dividendYieldValue.text = data.dividendYield?.let { "${CurrencyHelper.formatDecimal(it)}%" } ?: "-"

        if (data.lastUpdatedAt > 0) {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(data.lastUpdatedAt))
            binding.lastUpdatedText.text = "Uppdaterad $timeStr"
            binding.lastUpdatedText.isVisible = true
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
