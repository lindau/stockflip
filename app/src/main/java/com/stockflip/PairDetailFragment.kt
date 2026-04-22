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
import com.stockflip.ui.components.PairPerformanceChart
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

        binding.editPairButton.setOnClickListener {
            val data = (viewModel.pairState.value as? UiState.Success)?.data ?: return@setOnClickListener
            (requireActivity() as? MainActivity)?.showEditDialogFromPairs(data.watchItem)
        }
        binding.toggleActiveButton.setOnClickListener {
            viewModel.toggleActive()
            syncOverviewInBackground()
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
                        renderPair(state.data)
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
                            binding.spreadChartView.isVisible = true
                            binding.spreadChartView.setContent {
                                StockFlipTheme {
                                    PairPerformanceChart(
                                        data = state.data,
                                        selectedPeriod = period,
                                        onPeriodSelected = { viewModel.selectPeriod(it) }
                                    )
                                }
                            }
                        }
                        is UiState.Error -> {
                            binding.spreadChartView.isVisible = false
                        }
                    }
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyState.collect { history ->
                renderHistory(history)
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

        binding.toggleActiveButton.text = if (data.watchItem.isActive) "Inaktivera" else "Aktivera"
        binding.statusValue.text = if (data.watchItem.isActive) "Aktiv" else "Inaktiv"
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
        private const val ARG_WATCH_ITEM_ID = "watch_item_id"

        fun newInstance(watchItemId: Int): PairDetailFragment {
            return PairDetailFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_WATCH_ITEM_ID, watchItemId)
                }
            }
        }
    }
}
