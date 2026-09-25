package com.stockflip

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.stockflip.ui.SwipeToDeleteCallback
import com.stockflip.ui.WatchItemSkeletonList
import com.stockflip.ui.components.cards.PairCardPresentation
import com.stockflip.ui.theme.StockFlipTheme
import com.google.android.material.snackbar.Snackbar
import com.stockflip.databinding.FragmentPairsBinding
import kotlinx.coroutines.launch

class PairsFragment : Fragment() {

    private var _binding: FragmentPairsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext: Context = requireContext().applicationContext
                val database = StockPairDatabase.getDatabase(appContext)
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(
                    database.stockPairDao(),
                    database.watchItemDao(),
                    YahooFinanceService,
                    database.stockNoteDao(),
                    database.podcastObservationDao()
                ) as T
            }
        }
    }

    private lateinit var groupedAdapter: GroupedWatchItemAdapter
    private var pendingDeleteSnackbar: Snackbar? = null
    private var latestPairs: List<WatchItemUiState> = emptyList()
    // Fel från en misslyckad laddning utan data — ligger kvar tills en laddning lyckas.
    private var loadError: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPairsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.skeletonLoadingView.setContent {
            StockFlipTheme {
                WatchItemSkeletonList(count = 4)
            }
        }
        setupRecyclerView()
        setupEmptyStateRetry()
        setupObservers()

        // Fragmentet skapas om vid varje flikbyte — trigga en tyst refresh så listan alltid fylls.
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.refreshWatchItems(showLoading = false)
        }
    }

    private fun setupRecyclerView() {
        groupedAdapter = GroupedWatchItemAdapter(
            onToggleActive = { watchItem ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.toggleWatchItemActive(watchItem, !watchItem.isActive)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte uppdatera bevakning", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onReactivate = { watchItem ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val result = viewModel.reactivateWatchItem(watchItem)
                        Toast.makeText(requireContext(), result.toUserMessage(), Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte återaktivera bevakning", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDeleteClick = { watchItem ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        if (viewModel.deleteWatchItem(watchItem)) {
                            Toast.makeText(requireContext(), R.string.alert_deleted, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte ta bort aktiepar", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onEditClick = { watchItem ->
                (requireActivity() as? MainActivity)?.showEditDialogFromPairs(watchItem)
            },
            onItemClick = { watchItem ->
                (requireActivity() as? MainActivity)?.navigateToPairDetailFromPairs(watchItem.id)
            },
            pairCardPresentation = PairCardPresentation.Clarity,
            showPricePairHeader = false,
            showActiveToggleControls = false,
        )

        binding.pairsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = groupedAdapter
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Bakgrundsuppdateringen indikeras av backgroundRefreshIndicator-linjen;
                    // här stänger vi bara av SwipeRefreshLayouts egen dra-spinner när kallet är klart.
                    viewModel.refreshWatchItems(showLoading = false)
                } catch (e: Exception) {
                    // Felet ytas via watchItemUiState-observern (Snackbar).
                } finally {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        val swipeCallback = SwipeToDeleteCallback(
            context = requireContext(),
            canSwipe = { position ->
                groupedAdapter.currentList.getOrNull(position) is GroupedListItem.WatchItemWrapper
            },
            onSwiped = { position ->
                val listItem = groupedAdapter.currentList.getOrNull(position) as? GroupedListItem.WatchItemWrapper
                    ?: run {
                        groupedAdapter.notifyItemChanged(position)
                        return@SwipeToDeleteCallback
                    }
                // Återställ ItemTouchHelper-state direkt — DiffUtil animerar bort raden när Room uppdaterar
                groupedAdapter.notifyItemChanged(position)
                val itemToDelete = listItem.item
                pendingDeleteSnackbar?.dismiss()
                // Radera direkt i stället för när snackbaren stängs: en fördröjd radering knuten till
                // vyns livscykel gick förlorad (eller kraschade) om användaren lämnade fliken inom ångra-fönstret.
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // Vid fel visar MainActivity felet via actionError — ingen ångra-snackbar då.
                        if (!viewModel.deleteWatchItem(itemToDelete)) return@launch
                        val snackbar = Snackbar.make(binding.root, R.string.alert_deleted, Snackbar.LENGTH_LONG)
                        snackbar.setAction(R.string.alert_undo) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    viewModel.addWatchItem(itemToDelete)
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), e.message ?: "Kunde inte återställa aktiepar", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        snackbar.show()
                        pendingDeleteSnackbar = snackbar
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte ta bort aktiepar", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onSwipedRight = { position ->
                val listItem = groupedAdapter.currentList.getOrNull(position) as? GroupedListItem.WatchItemWrapper
                    ?: return@SwipeToDeleteCallback
                groupedAdapter.notifyItemChanged(position)
                val item = listItem.item
                binding.pairsRecyclerView.postDelayed({
                    (requireActivity() as? MainActivity)?.navigateToPairDetailFromPairs(item.id)
                }, 120)
            }
        )
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.pairsRecyclerView)
    }

    private fun setupEmptyStateRetry() {
        binding.emptyStateRetryButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                // showLoading = true: skelettet visas under försöket och ett nytt fel ytas som Error.
                viewModel.refreshWatchItems(showLoading = true)
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.watchItemUiState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            if (latestPairs.isEmpty()) {
                                binding.emptyStateContainer.visibility = View.GONE
                                binding.skeletonLoadingView.visibility = View.VISIBLE
                            }
                        }
                        is UiState.Success -> {
                            binding.skeletonLoadingView.visibility = View.GONE
                            val pairs = state.data.filter { it.item.watchType is WatchType.PricePair }
                            loadError = null
                            latestPairs = pairs
                            groupedAdapter.submitGroupedList(pairs)
                            renderEmptyState()
                        }
                        is UiState.Error -> {
                            binding.skeletonLoadingView.visibility = View.GONE
                            if (latestPairs.isEmpty()) {
                                // Visa felet istället för "Inga aktiepar ännu", som vore missvisande.
                                loadError = state.message
                                renderEmptyState()
                            } else {
                                Snackbar.make(binding.root, R.string.alerts_refresh_failed, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.watchItemsRefreshing.collect { refreshing ->
                    // Tunn linje istället för den runda spinnern — listan ligger kvar och är läsbar.
                    binding.backgroundRefreshIndicator.isVisible = refreshing
                }
            }
        }
    }

    private fun renderEmptyState() {
        val emptyState = watchListEmptyState(latestPairs.size, loadError)
        binding.emptyStateContainer.isVisible = emptyState !is WatchListEmptyState.Hidden
        binding.emptyStateRetryButton.isVisible = emptyState is WatchListEmptyState.LoadFailed
        when (emptyState) {
            WatchListEmptyState.Hidden -> Unit
            WatchListEmptyState.NoItems -> {
                binding.emptyStateTitle.setText(R.string.pairs_empty_title)
                binding.emptyStateText.setText(R.string.pairs_empty_subtitle)
            }
            is WatchListEmptyState.LoadFailed -> {
                binding.emptyStateTitle.setText(R.string.watch_items_load_failed_title)
                binding.emptyStateText.text = emptyState.message
            }
        }
    }

    override fun onDestroyView() {
        pendingDeleteSnackbar?.dismiss()
        pendingDeleteSnackbar = null
        super.onDestroyView()
        _binding = null
    }
}
