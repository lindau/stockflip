package com.stockflip

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.stockflip.ui.SwipeToDeleteCallback
import com.stockflip.databinding.FragmentStockDetailBinding
import com.stockflip.repository.MetricHistoryRepository
import com.stockflip.repository.TriggerHistoryRepository
import com.stockflip.viewmodel.StockSearchViewModel
import com.stockflip.repository.StockRepository
import com.stockflip.ui.dialogs.WatchDialogManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDivider
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.stockflip.ui.components.cards.ClarityStockDetailPanel
import com.stockflip.ui.theme.StockFlipTheme
import kotlin.math.abs

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
    private var latestStockData: StockDetailData? = null
    private var latestChartData: IntradayChartData? = null
    private var latestChartPeriod: ChartPeriod = ChartPeriod.DAY
    private var latestAlerts: List<WatchItemUiState> = emptyList()
    private var latestMetricHistory: Map<WatchType.MetricType, MetricHistorySummary> = emptyMap()
    private var latestInsiderTransactions: List<InsiderTransactionEntity> = emptyList()
    private var triggerBannerDismissed = false
    private var insiderNotificationHandled = false
    private var insiderTransactionsExpanded = false

    private fun syncOverviewInBackground() {
        (activity as? MainActivity)?.syncWatchItemsAfterDetailChange()
    }

    companion object {
        private const val TAG = "StockDetailFragment"
        private const val ARG_SYMBOL = "symbol"
        private const val ARG_COMPANY_NAME = "company_name"
        private const val ARG_HIGHLIGHT_WATCH_ID = "highlight_watch_id"
        private const val ARG_TRIGGER_TITLE = "trigger_title"
        private const val ARG_TRIGGER_MESSAGE = "trigger_message"
        private const val ARG_OPENED_FROM_NOTIFICATION = "opened_from_notification"
        private const val ARG_HIGHLIGHT_INSIDER_TRANSACTION_ID = "highlight_insider_transaction_id"
        private const val VERY_CLOSE_THRESHOLD = 0.05
        private const val CLOSE_THRESHOLD = 0.12
        private const val COLLAPSED_INSIDER_TRANSACTION_COUNT = 3

        /**
         * Skapar en ny instans av StockDetailFragment.
         */
        fun newInstance(
            symbol: String,
            companyName: String? = null,
            highlightWatchItemId: Int? = null,
            triggerTitle: String? = null,
            triggerMessage: String? = null,
            openedFromNotification: Boolean = false,
            highlightInsiderTransactionId: String? = null
        ): StockDetailFragment {
            return StockDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SYMBOL, symbol)
                    putString(ARG_COMPANY_NAME, companyName)
                    highlightWatchItemId?.let { putInt(ARG_HIGHLIGHT_WATCH_ID, it) }
                    putString(ARG_TRIGGER_TITLE, triggerTitle)
                    putString(ARG_TRIGGER_MESSAGE, triggerMessage)
                    putBoolean(ARG_OPENED_FROM_NOTIFICATION, openedFromNotification)
                    putString(ARG_HIGHLIGHT_INSIDER_TRANSACTION_ID, highlightInsiderTransactionId)
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
                        database.stockNoteDao(),
                        MetricHistoryRepository(database.metricHistoryDao()),
                        database.insiderTransactionDao()
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
            companyName = arguments?.getString(ARG_COMPANY_NAME),
            onWatchChanged = ::syncOverviewInBackground
        )

        setupRecyclerView()
        setupQuickActions()
        setupObservers()
        setupSwipeRefresh()

        binding.notesCard.setOnClickListener { dialogManager.showEditNoteDialog() }
        binding.triggerReactivateButton.setOnClickListener {
            val target = highlightedWatchItem() ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = viewModel.reactivateAlertAndReturnResult(target.item)
                    triggerBannerDismissed = true
                    binding.triggerBannerCard.isVisible = false
                    syncOverviewInBackground()
                    showReactivationToast(result)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.message ?: "Kunde inte återaktivera bevakning", Toast.LENGTH_LONG).show()
                }
            }
        }
        binding.triggerDeleteButton.setOnClickListener {
            val target = highlightedWatchItem() ?: return@setOnClickListener
            viewModel.deleteAlert(target.item)
            triggerBannerDismissed = true
            binding.triggerBannerCard.isVisible = false
            syncOverviewInBackground()
            Toast.makeText(requireContext(), "Bevakning borttagen", Toast.LENGTH_SHORT).show()
        }

        // Ladda data
        viewModel.loadStockData()
    }

    private fun setupRecyclerView() {
        alertAdapter = AlertAdapter(
            onToggleActive = { watchItem ->
                viewModel.toggleAlert(watchItem)
                syncOverviewInBackground()
            },
            onReactivate = { watchItem ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val result = viewModel.reactivateAlertAndReturnResult(watchItem)
                        syncOverviewInBackground()
                        showReactivationToast(result)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte återaktivera bevakning", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDelete = { watchItem ->
                dialogManager.showDeleteConfirmation(watchItem)
            },
            onEdit = { watchItem ->
                dialogManager.showEditWatchItemDialog(watchItem)
            },
            useVariantBackground = true,
            useClarityPresentation = true
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

        configureQuickActionButton(binding.createPriceTargetButton) {
            dialogManager.showCreatePriceTargetDialog()
        }

        configureQuickActionButton(binding.createDrawdownButton) {
            dialogManager.showCreateDrawdownDialog()
        }

        configureQuickActionButton(binding.createDailyMoveButton) {
            dialogManager.showCreateDailyMoveDialog()
        }

        configureQuickActionButton(binding.createKeyMetricsButton) {
            dialogManager.showCreateKeyMetricsDialog()
        }

        configureQuickActionButton(binding.createInsiderBuyButton) {
            dialogManager.showCreateInsiderBuyDialog()
        }
    }

    private fun configureQuickActionButton(
        button: View,
        onClick: () -> Unit
    ) {
        button.isVisible = true
        button.setOnClickListener { onClick() }
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
                        latestStockData = state.data
                        displayStockData(state.data)
                        renderClarityStockPanel()
                        renderDecisionSupport()
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
                        latestAlerts = state.data
                        alertAdapter.submitList(state.data)
                        binding.noAlertsText.isVisible = state.data.isEmpty()
                        binding.alertsRecyclerView.isVisible = state.data.isNotEmpty()
                        renderDecisionSupport()
                        renderInsiderTransactions()
                        renderTriggerBanner()
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
                    latestChartPeriod = period
                    binding.intradayChartView.isVisible = false
                    when (state) {
                        is UiState.Loading -> {
                            latestChartData = null
                            renderClarityStockPanel()
                        }
                        is UiState.Success -> {
                            latestChartData = state.data
                            renderClarityStockPanel()
                        }
                        is UiState.Error -> {
                            latestChartData = null
                            renderClarityStockPanel()
                        }
                    }
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.metricHistoryState.collect { metricHistory ->
                latestMetricHistory = metricHistory
                renderDecisionSupport()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.insiderTransactionsState.collect { transactions ->
                latestInsiderTransactions = transactions
                renderInsiderTransactions()
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
        binding.createInsiderBuyButton.visibility = if (canUseSecInsiderData(data)) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        
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
	        val hasAnyMetric = data.peRatio != null ||
	            data.psRatio != null ||
	            data.dividendYield != null ||
	            data.earningsPerShare != null
	        binding.keyMetricsRow.visibility = if (hasAnyMetric) android.view.View.VISIBLE else android.view.View.GONE
	        binding.peRatioValue.text = data.peRatio?.let { CurrencyHelper.formatDecimal(it) } ?: "-"
	        binding.psRatioValue.text = data.psRatio?.let { CurrencyHelper.formatDecimal(it) } ?: "-"
	        binding.dividendYieldValue.text = data.dividendYield?.let { "${CurrencyHelper.formatDecimal(it)}%" } ?: "-"
	        binding.earningsPerShareValue.text = data.earningsPerShare?.let { CurrencyHelper.formatDecimal(it) } ?: "-"
        binding.notesCard.isVisible = true

        if (data.lastUpdatedAt > 0) {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(data.lastUpdatedAt))
            binding.lastUpdatedText.text = "Uppdaterad $timeStr"
            binding.lastUpdatedText.isVisible = true
        }
    }

    private fun renderClarityStockPanel() {
        val data = latestStockData ?: return
        binding.stockClarityPanel.setContent {
            StockFlipTheme {
                ClarityStockDetailPanel(
                    data = data,
                    chartData = latestChartData,
                    selectedPeriod = latestChartPeriod,
                    onPeriodSelected = { viewModel.selectPeriod(it) },
                )
            }
        }
    }

    private fun renderDecisionSupport() {
        val data = latestStockData ?: return
        renderLevelsSection(data, latestAlerts)
        renderTriggerBanner()
    }

    private fun highlightedWatchItem(): WatchItemUiState? {
        val highlightId = arguments?.takeIf { it.containsKey(ARG_HIGHLIGHT_WATCH_ID) }?.getInt(ARG_HIGHLIGHT_WATCH_ID, -1)
            ?.takeIf { it > 0 }
        return when {
            highlightId != null -> latestAlerts.firstOrNull { it.item.id == highlightId }
            else -> latestAlerts.firstOrNull { it.item.isTriggered }
        }
    }

    private fun renderTriggerBanner() {
        if (triggerBannerDismissed) {
            binding.triggerBannerCard.isVisible = false
            return
        }
        val target = highlightedWatchItem()
        val openedFromNotification = arguments?.getBoolean(ARG_OPENED_FROM_NOTIFICATION, false) == true
        val shouldShow = openedFromNotification || (target?.item?.isTriggered == true)
        binding.triggerBannerCard.isVisible = shouldShow
        if (!shouldShow) return

        val stockData = latestStockData
        binding.triggerBannerTitle.text = arguments?.getString(ARG_TRIGGER_TITLE)
            ?: if (target != null && stockData != null) "Larm triggat: ${describeWatch(target.item, stockData)}" else "Larm triggat"
        binding.triggerBannerMessage.text = arguments?.getString(ARG_TRIGGER_MESSAGE)
            ?: "Öppnad från notis. Bevakningen är nu markerad som utlöst."
        binding.triggerReactivateButton.isVisible = target?.item?.isTriggered == true
        binding.triggerDeleteButton.isVisible = target != null
        target?.item?.let { TriggerSeenTracker.markSeen(it) }
    }

    private fun renderInsiderTransactions() {
        val hasInsiderWatch = latestAlerts.any { it.item.watchType is WatchType.InsiderBuy }
        val showSection = latestInsiderTransactions.isNotEmpty() || hasInsiderWatch
        binding.insiderSectionLabel.isVisible = showSection
        binding.insiderTransactionsCard.isVisible = showSection
        if (!showSection) return

        binding.insiderTransactionsContainer.removeAllViews()
        if (latestInsiderTransactions.isEmpty()) {
            binding.insiderEmptyText.isVisible = true
            binding.insiderEmptyText.text = "Inga insiderköp har hittats ännu."
            binding.insiderToggleText.isVisible = false
            return
        }

        binding.insiderEmptyText.isVisible = false
        val visibleTransactions = visibleInsiderTransactions()
        visibleTransactions.forEachIndexed { index, transaction ->
            if (index > 0) {
                binding.insiderTransactionsContainer.addView(
                    MaterialDivider(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dp(10)
                            bottomMargin = dp(10)
                        }
                    }
                )
            }
            binding.insiderTransactionsContainer.addView(createInsiderTransactionRow(transaction))
        }
        renderInsiderToggle(visibleTransactions.size)

        maybeHandleInsiderNotification()
    }

    private fun visibleInsiderTransactions(): List<InsiderTransactionEntity> {
        if (insiderTransactionsExpanded || latestInsiderTransactions.size <= COLLAPSED_INSIDER_TRANSACTION_COUNT) {
            return latestInsiderTransactions
        }

        val collapsed = latestInsiderTransactions.take(COLLAPSED_INSIDER_TRANSACTION_COUNT).toMutableList()
        val highlightId = arguments?.getString(ARG_HIGHLIGHT_INSIDER_TRANSACTION_ID)
        val highlighted = highlightId?.let { id -> latestInsiderTransactions.firstOrNull { it.id == id } }
        if (highlighted != null && collapsed.none { it.id == highlighted.id }) {
            collapsed[collapsed.lastIndex] = highlighted
        }
        return collapsed
    }

    private fun renderInsiderToggle(visibleCount: Int) {
        val totalCount = latestInsiderTransactions.size
        val canToggle = totalCount > COLLAPSED_INSIDER_TRANSACTION_COUNT
        binding.insiderToggleText.isVisible = canToggle
        if (!canToggle) {
            binding.insiderToggleText.setOnClickListener(null)
            binding.insiderSectionLabel.setOnClickListener(null)
            binding.insiderTransactionsCard.setOnClickListener(null)
            return
        }

        binding.insiderToggleText.text = if (insiderTransactionsExpanded) {
            "Visa färre insiderköp"
        } else {
            "Visar $visibleCount av $totalCount insiderköp · tryck för att visa alla"
        }
        val toggle = View.OnClickListener {
            insiderTransactionsExpanded = !insiderTransactionsExpanded
            renderInsiderTransactions()
            if (!insiderTransactionsExpanded) {
                binding.stockDetailScrollView.post {
                    binding.stockDetailScrollView.smoothScrollTo(0, binding.insiderSectionLabel.top)
                }
            }
        }
        binding.insiderToggleText.setOnClickListener(toggle)
        binding.insiderSectionLabel.setOnClickListener(toggle)
        binding.insiderTransactionsCard.setOnClickListener(toggle)
    }

    private fun createInsiderTransactionRow(transaction: InsiderTransactionEntity): View {
        val context = requireContext()
        val highlightId = arguments?.getString(ARG_HIGHLIGHT_INSIDER_TRANSACTION_ID)
        val isHighlighted = transaction.id == highlightId
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            if (isHighlighted) {
                setBackgroundColor(
                    MaterialColors.getColor(
                        binding.root,
                        com.google.android.material.R.attr.colorSecondaryContainer
                    )
                )
            }
            setOnClickListener { showInsiderTransactionSheet(transaction) }

            addView(TextView(context).apply {
                text = transaction.reportingOwner
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setTextColor(MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface))
            })
            addView(TextView(context).apply {
                text = insiderTransactionSummary(transaction)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
            addView(TextView(context).apply {
                text = insiderTransactionMeta(transaction)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                setTextColor(MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        }
    }

    private fun maybeHandleInsiderNotification() {
        if (insiderNotificationHandled) return
        val highlightId = arguments?.getString(ARG_HIGHLIGHT_INSIDER_TRANSACTION_ID) ?: return
        val highlighted = latestInsiderTransactions.firstOrNull { it.id == highlightId } ?: return
        insiderNotificationHandled = true
        binding.stockDetailScrollView.post {
            binding.stockDetailScrollView.smoothScrollTo(0, binding.insiderSectionLabel.top)
            showInsiderTransactionSheet(highlighted)
        }
    }

    private fun showInsiderTransactionSheet(transaction: InsiderTransactionEntity) {
        val dialog = BottomSheetDialog(requireContext())
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(16))
        }
        content.addView(TextView(requireContext()).apply {
            text = "Insiderköp"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTextColor(MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface))
        })
        listOf(
            "Person" to transaction.reportingOwner,
            "Roll" to (transaction.relationship ?: "Ej angiven"),
            "Datum" to transaction.transactionDate.ifBlank { transaction.filingDate ?: "Okänt datum" },
            "Instrument" to (transaction.securityTitle ?: transaction.symbol),
            "Antal" to (transaction.shares?.let { CurrencyHelper.formatDecimal(it) } ?: "Ej angivet"),
            "Pris" to (transaction.pricePerShare?.let { CurrencyHelper.formatPrice(it, insiderCurrency(transaction)) } ?: "Ej angivet"),
            "Värde" to (transaction.estimatedValue?.let { CurrencyHelper.formatPrice(it, insiderCurrency(transaction)) } ?: "Ej angivet"),
            "Rapporterad" to formatInsiderReportedDate(transaction)
        ).forEach { (label, value) ->
            content.addView(TextView(requireContext()).apply {
                text = "$label: $value"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, dp(10), 0, 0)
            })
        }
        content.addView(MaterialButton(requireContext()).apply {
            text = "Stäng"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(18)
            }
            setOnClickListener { dialog.dismiss() }
        })
        dialog.setContentView(content)
        dialog.show()
    }

    private fun insiderTransactionSummary(transaction: InsiderTransactionEntity): String {
        val shares = transaction.shares?.let { "${CurrencyHelper.formatDecimal(it)} aktier" } ?: "aktier"
        val value = transaction.estimatedValue?.let { " · ${CurrencyHelper.formatPrice(it, insiderCurrency(transaction))}" }.orEmpty()
        return "Köpte $shares$value"
    }

    private fun insiderTransactionMeta(transaction: InsiderTransactionEntity): String {
        return listOfNotNull(
            transaction.relationship,
            transaction.transactionDate.takeIf { it.isNotBlank() },
            transaction.securityTitle
        ).joinToString(" · ").ifBlank { "Detaljer saknas" }
    }

    private fun insiderCurrency(transaction: InsiderTransactionEntity): String {
        return if (transaction.cik == "FI" || transaction.symbol.endsWith(".ST", ignoreCase = true)) "SEK" else "USD"
    }

    private fun formatInsiderReportedDate(transaction: InsiderTransactionEntity): String {
        transaction.acceptedAtMillis?.let {
            return SimpleDateFormat("d MMM yyyy HH:mm", Locale("sv", "SE")).format(Date(it))
        }
        return transaction.filingDate ?: "Ej angivet"
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun renderLevelsSection(
        data: StockDetailData,
        alerts: List<WatchItemUiState>
    ) {
        val activeAlerts = alerts.filter { it.item.isActive && !it.item.isTriggered }
        val nearest = activeAlerts
            .mapNotNull { uiState ->
                calculateTriggerProximity(uiState)?.let { proximity -> uiState to proximity }
            }
            .minByOrNull { it.second }

        if (nearest == null) {
            binding.levelsHeadlineText.text = "Inga aktiva nivåer ännu"
            binding.levelsSummaryText.text = "Skapa pris-, drawdown- eller värderingsnivåer för att få snabbare beslutsstöd här."
        } else {
            binding.levelsHeadlineText.text = "Närmast trigger: ${describeWatch(nearest.first.item, data)}"
            binding.levelsSummaryText.text = buildNearestSummary(nearest.first, nearest.second, activeAlerts.size, data)
        }

        binding.levelsPriceTargetText.text = buildLevelLine(
            prefix = "Målpris",
            uiState = activeAlerts
                .filter { it.item.watchType is WatchType.PriceTarget }
                .minByOrNull { calculateTriggerProximity(it) ?: Double.MAX_VALUE },
            fallback = "Ingen prismålsnivå än"
        ) { state ->
            val watchType = state.item.watchType as WatchType.PriceTarget
            val priceText = CurrencyHelper.formatPrice(watchType.targetPrice, data.currency)
            val directionText = watchType.direction.swedishDirectionLabel()
            val progress = state.live.currentPrice.takeIf { it > 0.0 }?.let {
                " · ${CurrencyHelper.formatDecimal(percentGapToPriceTarget(state))}% kvar"
            }.orEmpty()
            "$directionText $priceText$progress"
        }
    }

    private fun showReactivationToast(result: WatchReactivationResult) {
        Toast.makeText(
            requireContext(),
            result.toUserMessage(latestStockData?.currency),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun buildNearestSummary(
        uiState: WatchItemUiState,
        proximity: Double,
        activeAlertCount: Int,
        data: StockDetailData
    ): String {
        val headline = when (val watchType = uiState.item.watchType) {
            is WatchType.PriceTarget -> "${CurrencyHelper.formatDecimal(percentGapToPriceTarget(uiState))}% kvar till ${CurrencyHelper.formatPrice(watchType.targetPrice, data.currency)}"
            is WatchType.ATHBased -> when (watchType.dropType) {
                WatchType.DropType.PERCENTAGE -> {
                    val currentDrop = uiState.live.currentDropPercentage.takeIf { it > 0.0 }
                        ?: when (watchType.reference) {
                            WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> data.drawdownPercent
                            WatchType.HighReference.ALL_TIME_HIGH -> data.allTimeDrawdownPercent
                        }
                        ?: 0.0
                    "${CurrencyHelper.formatDecimal((watchType.dropValue - currentDrop).coerceAtLeast(0.0))} procentenheter kvar till drawdown-nivån"
                }

                WatchType.DropType.ABSOLUTE -> {
                    val currentDrop = uiState.live.currentDropAbsolute.takeIf { it > 0.0 }
                        ?: when (watchType.reference) {
                            WatchType.HighReference.FIFTY_TWO_WEEK_HIGH ->
                                if (data.week52High != null && data.lastPrice != null) data.week52High - data.lastPrice else null
                            WatchType.HighReference.ALL_TIME_HIGH ->
                                if (data.allTimeHigh != null && data.lastPrice != null) data.allTimeHigh - data.lastPrice else null
                        }
                        ?: 0.0
                    "${CurrencyHelper.formatPrice((watchType.dropValue - currentDrop).coerceAtLeast(0.0), data.currency)} kvar till drawdown-nivån"
                }
            }

            is WatchType.KeyMetrics -> {
                val currentValue = currentMetricValueFor(watchType.metricType, data)
                if (currentValue == null) {
                    "${metricLabel(watchType.metricType)} bevakas"
                } else {
                    "${formatMetricValue(watchType.metricType, abs(watchType.targetValue - currentValue))} kvar till din ${metricLabel(watchType.metricType)}-nivå"
                }
            }

            is WatchType.DailyMove -> {
                val currentMove = currentDailyMoveFor(uiState)
                "${CurrencyHelper.formatDecimal((watchType.percentThreshold - currentMove).coerceAtLeast(0.0))}% kvar till dagsrörelsen"
            }

            is WatchType.PriceRange -> "Pris bevakas inom ditt intervall"
            is WatchType.InsiderBuy -> "Insiderköp bevakas dagligen"
            is WatchType.PricePair -> "Parbevakning"
            is WatchType.Combined -> "Kombinerat villkor"
        }
        return "$headline. Du har $activeAlertCount aktiva bevakningar för bolaget."
    }

    private fun buildLevelLine(
        prefix: String,
        uiState: WatchItemUiState?,
        fallback: String,
        renderer: (WatchItemUiState) -> String
    ): String {
        return if (uiState != null) "$prefix: ${renderer(uiState)}" else "$prefix: $fallback"
    }

    private fun calculateTriggerProximity(uiState: WatchItemUiState): Double? {
        val live = uiState.live
        return when (val watchType = uiState.item.watchType) {
            is WatchType.PriceTarget -> {
                if (live.currentPrice <= 0.0 || watchType.targetPrice <= 0.0) null
                else when (watchType.direction) {
                    WatchType.PriceDirection.ABOVE -> ((watchType.targetPrice - live.currentPrice).coerceAtLeast(0.0) / watchType.targetPrice)
                    WatchType.PriceDirection.BELOW -> ((live.currentPrice - watchType.targetPrice).coerceAtLeast(0.0) / watchType.targetPrice)
                }
            }

            is WatchType.KeyMetrics -> {
                if (live.currentMetricValue <= 0.0 || watchType.targetValue <= 0.0) null
                else when (watchType.direction) {
                    WatchType.PriceDirection.ABOVE -> ((watchType.targetValue - live.currentMetricValue).coerceAtLeast(0.0) / watchType.targetValue)
                    WatchType.PriceDirection.BELOW -> ((live.currentMetricValue - watchType.targetValue).coerceAtLeast(0.0) / watchType.targetValue)
                }
            }

            is WatchType.ATHBased -> {
                val currentValue = when (watchType.dropType) {
                    WatchType.DropType.PERCENTAGE -> live.currentDropPercentage
                    WatchType.DropType.ABSOLUTE -> live.currentDropAbsolute
                }
                if (currentValue <= 0.0 || watchType.dropValue <= 0.0) null
                else ((watchType.dropValue - currentValue).coerceAtLeast(0.0) / watchType.dropValue)
            }

            is WatchType.DailyMove -> {
                if (watchType.percentThreshold <= 0.0) null
                else ((watchType.percentThreshold - currentDailyMoveFor(uiState)).coerceAtLeast(0.0) / watchType.percentThreshold)
            }

            is WatchType.PriceRange -> {
                if (live.currentPrice <= 0.0) null
                else when {
                    live.currentPrice in watchType.minPrice..watchType.maxPrice -> 0.0
                    live.currentPrice < watchType.minPrice -> abs(live.currentPrice - watchType.minPrice) / watchType.minPrice
                    else -> abs(live.currentPrice - watchType.maxPrice) / watchType.maxPrice
                }
            }

            is WatchType.PricePair -> null
            is WatchType.InsiderBuy -> null
            is WatchType.Combined -> null
        }
    }

    private fun currentDailyMoveFor(uiState: WatchItemUiState): Double {
        val watchType = uiState.item.watchType as? WatchType.DailyMove ?: return 0.0
        val currentChange = uiState.live.currentDailyChangePercent ?: return 0.0
        return when (watchType.direction) {
            WatchType.DailyMoveDirection.UP -> currentChange.coerceAtLeast(0.0)
            WatchType.DailyMoveDirection.DOWN -> (-currentChange).coerceAtLeast(0.0)
            WatchType.DailyMoveDirection.BOTH -> abs(currentChange)
        }
    }

    private fun currentMetricValueFor(metricType: WatchType.MetricType, data: StockDetailData): Double? {
        return when (metricType) {
	            WatchType.MetricType.PE_RATIO -> data.peRatio
	            WatchType.MetricType.PS_RATIO -> data.psRatio
	            WatchType.MetricType.DIVIDEND_YIELD -> data.dividendYield
	            WatchType.MetricType.EARNINGS_PER_SHARE -> data.earningsPerShare
	        }
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

    private fun directionLabel(direction: WatchType.PriceDirection): String {
        return when (direction) {
            WatchType.PriceDirection.ABOVE -> "över"
            WatchType.PriceDirection.BELOW -> "under"
        }
    }

    private fun highReferenceLabel(reference: WatchType.HighReference): String {
        return when (reference) {
            WatchType.HighReference.FIFTY_TWO_WEEK_HIGH -> "52v högsta"
            WatchType.HighReference.ALL_TIME_HIGH -> "högsta pris"
        }
    }

    private fun describeWatch(item: WatchItem, data: StockDetailData): String {
        return when (val watchType = item.watchType) {
            is WatchType.PriceTarget -> "pris ${directionLabel(watchType.direction)} ${CurrencyHelper.formatPrice(watchType.targetPrice, data.currency)}"
            is WatchType.ATHBased -> when (watchType.dropType) {
                WatchType.DropType.PERCENTAGE -> "drawdown från ${highReferenceLabel(watchType.reference)} ${CurrencyHelper.formatDecimal(watchType.dropValue)}%"
                WatchType.DropType.ABSOLUTE -> "drawdown från ${highReferenceLabel(watchType.reference)} ${CurrencyHelper.formatPrice(watchType.dropValue, data.currency)}"
            }

            is WatchType.KeyMetrics -> "${metricLabel(watchType.metricType)} ${directionLabel(watchType.direction)} ${formatMetricValue(watchType.metricType, watchType.targetValue)}"
            is WatchType.DailyMove -> "dagsrörelse ${CurrencyHelper.formatDecimal(watchType.percentThreshold)}%"
            is WatchType.PriceRange -> "pris inom ${CurrencyHelper.formatPrice(watchType.minPrice, data.currency)} - ${CurrencyHelper.formatPrice(watchType.maxPrice, data.currency)}"
            is WatchType.InsiderBuy -> "insiderköp"
            is WatchType.PricePair -> "aktiepar"
            is WatchType.Combined -> "kombinerat larm"
        }
    }

    private fun canUseSecInsiderData(data: StockDetailData): Boolean {
        return !StockSearchResult.isCryptoSymbol(data.symbol) &&
            (
                (data.currency.equals("USD", ignoreCase = true) && !data.symbol.contains(".")) ||
                    data.symbol.endsWith(".ST", ignoreCase = true) ||
                    data.symbol.endsWith(".STO", ignoreCase = true)
                )
    }

    private fun percentGapToPriceTarget(uiState: WatchItemUiState): Double {
        val watchType = uiState.item.watchType as? WatchType.PriceTarget ?: return 0.0
        val currentPrice = uiState.live.currentPrice.takeIf { it > 0.0 } ?: return 0.0
        return when (watchType.direction) {
            WatchType.PriceDirection.ABOVE -> ((watchType.targetPrice - currentPrice).coerceAtLeast(0.0) / watchType.targetPrice) * 100
            WatchType.PriceDirection.BELOW -> ((currentPrice - watchType.targetPrice).coerceAtLeast(0.0) / watchType.targetPrice) * 100
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
