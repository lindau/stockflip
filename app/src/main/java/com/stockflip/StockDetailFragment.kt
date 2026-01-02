package com.stockflip

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.stockflip.databinding.FragmentStockDetailBinding
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
        
        binding.dailyChangePercent.text = data.dailyChangePercent?.let { change ->
            val sign = if (change >= 0) "+" else ""
            val color = if (change >= 0) {
                android.graphics.Color.parseColor("#4CAF50") // Green
            } else {
                android.graphics.Color.parseColor("#F44336") // Red
            }
            binding.dailyChangePercent.setTextColor(color)
            "$sign${priceFormat.format(change)}%"
        } ?: "Laddar..."
        
        binding.week52High.text = data.week52High?.let {
            "${priceFormat.format(it)} SEK"
        } ?: "Laddar..."
        
        binding.drawdownPercent.text = data.drawdownPercent?.let {
            "${priceFormat.format(it)}%"
        } ?: "Laddar..."
    }

    private fun showCreatePriceTargetDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_price_target, null)
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput)
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput)
        
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_ath_based, null)
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput)
        
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
                val dropValueStr = dropValueInput.text.toString()

                if (dropValueStr.isNotEmpty()) {
                    val dropValue = dropValueStr.toDoubleOrNull()

                    if (dropValue != null && dropValue > 0) {
                        val companyName = (viewModel.stockDataState.value as? UiState.Success<StockDetailData>)?.data?.companyName
                            ?: arguments?.getString(ARG_COMPANY_NAME) ?: ""
                        
                        viewModel.createAlert(
                            WatchType.ATHBased(WatchType.DropType.PERCENTAGE, dropValue),
                            companyName
                        )
                        Toast.makeText(requireContext(), "Drawdown-bevakning skapad", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ange ett giltigt värde", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Ange ett värde", Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

