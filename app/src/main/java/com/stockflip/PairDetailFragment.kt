package com.stockflip

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.stockflip.databinding.FragmentPairDetailBinding
import com.stockflip.repository.TriggerHistoryRepository
import com.stockflip.ui.components.cards.ClarityPairDetailPanel
import com.stockflip.ui.theme.StockFlipTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PairDetailFragment : Fragment() {

    private var _binding: FragmentPairDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PairDetailViewModel
    private var triggerBannerDismissed = false
    private var latestPairData: PairDetailData? = null
    private var latestChartData: PairChartData? = null
    private var latestChartPeriod: ChartPeriod = ChartPeriod.DAY
    private var latestHistory: List<Long> = emptyList()

    private fun syncOverviewInBackground() {
        (activity as? MainActivity)?.syncWatchItemsAfterDetailChange()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPairDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val watchItemId = arguments?.getInt(ARG_WATCH_ITEM_ID, -1) ?: -1
        if (watchItemId <= 0) {
            Toast.makeText(requireContext(), "Ogiltigt aktiepar", Toast.LENGTH_SHORT).show()
            @Suppress("DEPRECATION")
            requireActivity().onBackPressed()
            return
        }

        val database = StockPairDatabase.getDatabase(requireContext())
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PairDetailViewModel(
                    database.watchItemDao(),
                    YahooFinanceService,
                    watchItemId,
                    TriggerHistoryRepository(database.triggerHistoryDao())
                ) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[PairDetailViewModel::class.java]

        binding.editPairButton.setOnClickListener { editPair() }
        binding.triggerReactivateButton.setOnClickListener {
            viewModel.reactivate()
            triggerBannerDismissed = true
            binding.triggerBannerCard.isVisible = false
            syncOverviewInBackground()
            Toast.makeText(requireContext(), "Bevakning återaktiverad", Toast.LENGTH_SHORT).show()
        }
        binding.triggerDeleteButton.setOnClickListener {
            viewModel.deletePair()
            triggerBannerDismissed = true
            binding.triggerBannerCard.isVisible = false
            syncOverviewInBackground()
            Toast.makeText(requireContext(), "Bevakning borttagen", Toast.LENGTH_SHORT).show()
            @Suppress("DEPRECATION")
            requireActivity().onBackPressed()
        }

        observeState()
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refresh()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pairState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.loadingIndicator.isVisible = true
                    }
                    is UiState.Success -> {
                        binding.loadingIndicator.isVisible = false
                        latestPairData = state.data
                        try {
                            renderPair(state.data)
                            renderClarityPairPanel()
                            renderTriggerBanner(state.data)
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "Error rendering pair detail: ${e.message}", e)
                            Toast.makeText(requireContext(), "Kunde inte visa aktieparet", Toast.LENGTH_LONG).show()
                        }
                    }
                    is UiState.Error -> {
                        binding.loadingIndicator.isVisible = false
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.chartState, viewModel.selectedPeriod) { state, period -> state to period }
                .collect { (state, period) ->
                    when (state) {
                        is UiState.Loading -> { /* no-op */ }
                        is UiState.Success -> {
                            try {
                                latestChartData = state.data
                                latestChartPeriod = period
                                binding.spreadChartView.isVisible = false
                                renderClarityPairPanel()
                            } catch (e: Exception) {
                                android.util.Log.e(TAG, "Error rendering pair chart: ${e.message}", e)
                                binding.spreadChartView.isVisible = false
                            }
                        }
                        is UiState.Error -> {
                            latestChartData = null
                            binding.spreadChartView.isVisible = false
                            renderClarityPairPanel()
                        }
                    }
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyState.collect { history ->
                latestHistory = history
                renderHistory(history)
                renderClarityPairPanel()
            }
        }
    }

    private fun editPair() {
        val data = latestPairData ?: (viewModel.pairState.value as? UiState.Success)?.data ?: return
        (requireActivity() as? MainActivity)?.showEditDialogFromPairs(data.watchItem)
    }

    private fun renderClarityPairPanel() {
        val data = latestPairData ?: return
        binding.pairClarityPanel.setContent {
            StockFlipTheme {
                ClarityPairDetailPanel(
                    data = data,
                    chartData = latestChartData,
                    selectedPeriod = latestChartPeriod,
                    history = latestHistory,
                    onPeriodSelected = { viewModel.selectPeriod(it) },
                    onEdit = { editPair() },
                )
            }
        }
    }

    private fun renderPair(data: PairDetailData) {
        val pair = data.watchItem.watchType as WatchType.PricePair
        val a = data.stockA
        val b = data.stockB

        binding.stockALabel.text = "${a.companyName ?: a.symbol} (${a.symbol})"
        binding.stockBLabel.text = "${b.companyName ?: b.symbol} (${b.symbol})"

        binding.stockAPrice.text = formatPrice(a.lastPrice, a.currency)
        binding.stockBPrice.text = formatPrice(b.lastPrice, b.currency)
        bindChange(binding.stockAChange, a.dailyChangePercent)
        bindChange(binding.stockBChange, b.dailyChangePercent)

        binding.spreadValue.text = data.spread?.let { CurrencyHelper.formatDecimal(it) } ?: "—"
        binding.spreadTarget.text = "Mål: ${CurrencyHelper.formatDecimal(pair.priceDifference)}"
        binding.notifyEqualValue.text = if (pair.notifyWhenEqual) "Ja" else "Nej"

        binding.statusValue.text = if (data.watchItem.isActive) "Aktiv" else "Inaktiv"
    }

    private fun renderTriggerBanner(data: PairDetailData) {
        if (triggerBannerDismissed) {
            binding.triggerBannerCard.isVisible = false
            return
        }
        val triggerTitle = arguments?.getString(ARG_TRIGGER_TITLE)
        val triggerMessage = arguments?.getString(ARG_TRIGGER_MESSAGE)
        val openedFromNotification = arguments?.getBoolean(ARG_OPENED_FROM_NOTIFICATION, false) == true
        val shouldShow = openedFromNotification || data.watchItem.isTriggered
        binding.triggerBannerCard.isVisible = shouldShow
        if (!shouldShow) return

        binding.triggerBannerTitle.text = triggerTitle ?: "Larm triggat: ${data.watchItem.getDisplayName()}"
        binding.triggerBannerMessage.text = triggerMessage
            ?: "Öppnad från notis. Bevakningen är nu markerad som utlöst."
        binding.triggerReactivateButton.isVisible = data.watchItem.isTriggered
    }

    private fun renderHistory(history: List<Long>) {
        if (history.isEmpty()) {
            binding.historyValue.text = "Ingen historik"
            return
        }
        val formatter = SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault())
        val text = history.joinToString("\n") { formatter.format(Date(it)) }
        binding.historyValue.text = text
    }

    private fun formatPrice(price: Double?, currency: String?): String {
        if (price == null) return "—"
        val code = currency ?: "SEK"
        return CurrencyHelper.formatPrice(price, code)
    }

    private fun formatChange(change: Double): String {
        val sign = if (change >= 0) "+" else ""
        return "$sign${CurrencyHelper.formatDecimal(change)}%"
    }

    private fun bindChange(view: android.widget.TextView, change: Double?) {
        view.isVisible = change != null
        if (change != null) {
            view.text = formatChange(change)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "PairDetailFragment"
        private const val ARG_WATCH_ITEM_ID = "watch_item_id"
        private const val ARG_TRIGGER_TITLE = "trigger_title"
        private const val ARG_TRIGGER_MESSAGE = "trigger_message"
        private const val ARG_OPENED_FROM_NOTIFICATION = "opened_from_notification"

        fun newInstance(
            watchItemId: Int,
            triggerTitle: String? = null,
            triggerMessage: String? = null,
            openedFromNotification: Boolean = false
        ): PairDetailFragment {
            return PairDetailFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_WATCH_ITEM_ID, watchItemId)
                    putString(ARG_TRIGGER_TITLE, triggerTitle)
                    putString(ARG_TRIGGER_MESSAGE, triggerMessage)
                    putBoolean(ARG_OPENED_FROM_NOTIFICATION, openedFromNotification)
                }
            }
        }
    }
}
