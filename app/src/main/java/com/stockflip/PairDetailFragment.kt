package com.stockflip

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.stockflip.databinding.FragmentPairDetailBinding
import com.stockflip.repository.TriggerHistoryRepository
import com.stockflip.ui.components.cards.ClarityPairDetailPanel
import com.stockflip.ui.theme.StockFlipTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

    fun refreshDetail() {
        if (::viewModel.isInitialized) {
            viewModel.refresh()
        }
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

        binding.triggerReactivateButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = viewModel.reactivateAndReturnResult()
                    triggerBannerDismissed = true
                    binding.triggerBannerCard.isVisible = false
                    syncOverviewInBackground()
                    Toast.makeText(
                        requireContext(),
                        result?.toUserMessage() ?: "Bevakning återaktiverad",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.message ?: "Kunde inte återaktivera bevakning", Toast.LENGTH_LONG).show()
                }
            }
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
        refreshDetail()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.pairState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.loadingIndicator.isVisible = true
                    }
                    is UiState.Success -> {
                        binding.loadingIndicator.isVisible = false
                        latestPairData = state.data
                        try {
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
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            combine(viewModel.chartState, viewModel.selectedPeriod) { state, period -> state to period }
                .collect { (state, period) ->
                    when (state) {
                        is UiState.Loading -> { /* no-op */ }
                        is UiState.Success -> {
                            try {
                                latestChartData = state.data
                                latestChartPeriod = period
                                renderClarityPairPanel()
                            } catch (e: Exception) {
                                android.util.Log.e(TAG, "Error rendering pair chart: ${e.message}", e)
                            }
                        }
                        is UiState.Error -> {
                            latestChartData = null
                            renderClarityPairPanel()
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.historyState.collect { history ->
                latestHistory = history
                renderClarityPairPanel()
            }
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
        TriggerSeenTracker.markSeen(data.watchItem)
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
