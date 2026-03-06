package com.stockflip

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.stockflip.ui.SwipeToDeleteCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stockflip.databinding.FragmentAlertsBinding
import kotlinx.coroutines.launch

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
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
                    YahooFinanceService
                ) as T
            }
        }
    }

    private lateinit var alertAdapter: AlertAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        alertAdapter = AlertAdapter(
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
                        viewModel.reactivateWatchItem(watchItem)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte återaktivera bevakning", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDelete = { watchItem ->
                showDeleteConfirmation(watchItem)
            },
            onEdit = { watchItem ->
                (requireActivity() as? MainActivity)?.showEditDialogFromAlerts(watchItem)
            }
        )

        binding.alertsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alertAdapter
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    viewModel.refreshWatchItems()
                } catch (e: Exception) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        val swipeCallback = SwipeToDeleteCallback(
            context = requireContext(),
            onSwiped = { position ->
                val item = alertAdapter.currentList.getOrNull(position) ?: return@SwipeToDeleteCallback
                alertAdapter.notifyItemChanged(position)
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.deleteWatchItem(item)
                        Toast.makeText(requireContext(), R.string.alert_deleted, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte ta bort bevakning", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onSwipedRight = { position ->
                val item = alertAdapter.currentList.getOrNull(position) ?: return@SwipeToDeleteCallback
                alertAdapter.notifyItemChanged(position)
                (requireActivity() as? MainActivity)?.navigateToStockDetailFromAlerts(
                    symbol = item.ticker ?: item.ticker1 ?: return@SwipeToDeleteCallback,
                    companyName = item.companyName
                )
            }
        )
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.alertsRecyclerView)
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.watchItemUiState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            if (!binding.swipeRefreshLayout.isRefreshing) {
                                binding.alertsProgressBar.visibility = View.VISIBLE
                            }
                        }
                        is UiState.Success -> {
                            binding.alertsProgressBar.visibility = View.GONE
                            binding.swipeRefreshLayout.isRefreshing = false
                            val items = state.data
                            alertAdapter.submitList(items)
                            binding.emptyStateText.visibility = if (items.isEmpty()) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }
                        }
                        is UiState.Error -> {
                            binding.alertsProgressBar.visibility = View.GONE
                            binding.swipeRefreshLayout.isRefreshing = false
                            binding.emptyStateText.visibility = View.VISIBLE
                            binding.emptyStateText.text = state.message
                        }
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmation(watchItem: WatchItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.alert_delete_title)
            .setMessage(R.string.alert_delete_message)
            .setPositiveButton(R.string.alert_delete_positive) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.deleteWatchItem(watchItem)
                        Toast.makeText(requireContext(), R.string.alert_deleted, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Kunde inte ta bort bevakning", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.alert_delete_negative, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG: String = "AlertsFragment"
    }
}

