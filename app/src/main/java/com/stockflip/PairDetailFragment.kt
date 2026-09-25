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
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
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
                    // null betyder att aktieparet inte var inläst — då har inget återaktiverats.
                    val result = viewModel.reactivateAndReturnResult()
                    if (result == null) {
                        Toast.makeText(requireContext(), R.string.pair_reactivate_failed, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    triggerBannerDismissed = true
                    binding.triggerBannerCard.isVisible = false
                    syncOverviewInBackground()
                    Toast.makeText(requireContext(), result.toUserMessage(), Toast.LENGTH_LONG).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), R.string.pair_reactivate_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
        binding.triggerDeleteButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                // Bekräfta och lämna vyn först när borttagningen faktiskt lyckats.
                if (!viewModel.deletePair()) {
                    Toast.makeText(requireContext(), R.string.pair_delete_failed, Toast.LENGTH_LONG).show()
                    return@launch
                }
                triggerBannerDismissed = true
                binding.triggerBannerCard.isVisible = false
                syncOverviewInBackground()
                Toast.makeText(requireContext(), R.string.pair_deleted, Toast.LENGTH_SHORT).show()
                @Suppress("DEPRECATION")
                requireActivity().onBackPressed()
            }
        }
        binding.pairRetryButton.setOnClickListener {
            viewModel.refresh()
        }

        observeState()
    }

    override fun onResume() {
        super.onResume()
        refreshDetail()
    }

    override fun onPause() {
        super.onPause()
        // Säkerhetsnät: tvinga alltid fram en färsk omladdning av översikten när
        // aktieparsidan lämnas, se motsvarande kommentar i StockDetailFragment.
        syncOverviewInBackground()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.pairState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.pairErrorContainer.isVisible = false
                        binding.loadingIndicator.isVisible = true
                    }
                    is UiState.Success -> {
                        binding.pairErrorContainer.isVisible = false
                        binding.loadingIndicator.isVisible = false
                        binding.pairClarityPanel.isVisible = true
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
                        // ViewModel emitterar Error bara när ingen data kan visas
                        // (misslyckad första laddning eller ogiltigt/borttaget aktiepar).
                        binding.loadingIndicator.isVisible = false
                        binding.pairClarityPanel.isVisible = false
                        binding.triggerBannerCard.isVisible = false
                        binding.pairErrorText.text = state.message
                        binding.pairErrorContainer.isVisible = true
                    }
                }
            }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refreshFailed.collect {
                    Snackbar.make(binding.root, R.string.alerts_refresh_failed, Snackbar.LENGTH_LONG).show()
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
