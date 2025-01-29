package com.stockflip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Filter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.stockflip.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import com.stockflip.viewmodel.StockSearchViewModel
import com.stockflip.repository.StockRepository
import com.stockflip.repository.SearchState

/**
 * Main activity for the StockFlip application.
 * Handles the UI for displaying, adding, editing, and deleting stock pairs.
 * Manages real-time stock price updates and user notifications.
 */
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = StockPairDatabase.getDatabase(applicationContext)
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(database.stockPairDao(), YahooFinanceService) as T
            }
        }
    }
    private lateinit var binding: ActivityMainBinding
    private val priceUpdateReceiver = PriceUpdateReceiver()
    private var refreshJob: Job? = null
    private var selectedStock1: StockSearchResult? = null
    private var selectedStock2: StockSearchResult? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required for alerts", Toast.LENGTH_LONG).show()
        }
    }
    private val stockSearchViewModel1: StockSearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StockSearchViewModel(StockRepository()) as T
            }
        }
    }
    
    private val stockSearchViewModel2: StockSearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StockSearchViewModel(StockRepository()) as T
            }
        }
    }

    /**
     * Initializes the activity's UI components and starts data loading.
     * Sets up the view binding, initializes UI elements, and requests necessary permissions.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializeUI()
        initializeUpdates()
        requestPermissions()
        loadInitialData()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterPriceUpdateReceiver()
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    private fun initializeUI(): Unit {
        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
        setupAddButton()
    }

    private fun initializeUpdates() {
        StockPriceUpdater.startPeriodicUpdate(this)
        registerPriceUpdateReceiver()
        StockPriceUpdater.requestBatteryOptimizationExemption(this)
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
    }

    private fun loadInitialData(): Unit {
        lifecycleScope.launch {
            viewModel.loadStockPairs()
        }
    }

    private fun registerPriceUpdateReceiver() {
        try {
            registerReceiver(
                priceUpdateReceiver,
                PriceUpdateReceiver.createIntentFilter(),
                Context.RECEIVER_NOT_EXPORTED
            )
            Log.d(TAG, "Successfully registered price update receiver")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register price update receiver: ${e.message}")
        }
    }

    private fun unregisterPriceUpdateReceiver() {
        try {
            unregisterReceiver(priceUpdateReceiver)
            Log.d(TAG, "Successfully unregistered price update receiver")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister price update receiver: ${e.message}")
        }
    }

    /**
     * Starts automatic refresh of stock prices.
     * Cancels any existing refresh job before starting a new one.
     */
    internal fun startAutoRefresh(): Unit {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    Log.d(TAG, "Auto-refreshing stock prices")
                    viewModel.refreshStockPairs()
                    updateLastUpdateTime()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during auto-refresh: ${e.message}")
                }
                delay(AUTO_REFRESH_INTERVAL)
            }
        }
    }

    /**
     * Stops the automatic refresh of stock prices.
     * Cancels the refresh job if it exists.
     */
    private fun stopAutoRefresh(): Unit {
        refreshJob?.cancel()
        refreshJob = null
    }

    /**
     * Updates the last update time display in the UI.
     * Uses the TIME_FORMAT pattern defined in companion object.
     */
    private fun updateLastUpdateTime(): Unit {
        val currentTime: String = SimpleDateFormat(TIME_FORMAT, Locale.getDefault()).format(Date())
        binding.lastUpdateTime.text = getString(R.string.last_updated, currentTime)
        Log.d(TAG, "Updated last update time to $currentTime")
    }

    /**
     * Sets up observers for UI state changes.
     * Handles loading, success, and error states.
     */
    private fun setupObservers(): Unit {
        lifecycleScope.launch {
            viewModel.uiState.collect { state: UiState<List<StockPair>> ->
                Log.d(TAG, "Received UI state update: $state")
                handleUiState(state)
            }
        }
    }

    /**
     * Handles different UI states and updates the view accordingly.
     *
     * @param state The current UI state to handle
     */
    internal fun handleUiState(state: UiState<List<StockPair>>): Unit {
        when (state) {
            is UiState.Loading -> showLoading()
            is UiState.Success -> showSuccess(state.data)
            is UiState.Error -> showError(state.message)
        }
    }

    private fun showLoading(): Unit {
        Log.d(TAG, "Showing loading state")
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun showSuccess(data: List<StockPair>): Unit {
        Log.d(TAG, "Received ${data.size} stock pairs")
        binding.progressBar.visibility = View.GONE
        (binding.stockPairsList.adapter as StockPairAdapter).submitList(data)
    }

    private fun showError(message: String): Unit {
        Log.e(TAG, "Error state: $message")
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setupRecyclerView(): Unit {
        Log.d(TAG, "Setting up RecyclerView")
        binding.stockPairsList.layoutManager = LinearLayoutManager(this)
        binding.stockPairsList.adapter = StockPairAdapter(
            onDeleteClick = { pair: StockPair -> handleDeleteClick(pair) },
            onEditClick = { pair: StockPair -> handleEditClick(pair) }
        )
    }

    private fun handleDeleteClick(pair: StockPair): Unit {
        Log.d(TAG, "Delete clicked for pair: ${pair.companyName1} - ${pair.companyName2}")
        showDeleteConfirmationDialog(pair)
    }

    private fun handleEditClick(pair: StockPair): Unit {
        Log.d(TAG, "Edit clicked for pair: ${pair.companyName1} - ${pair.companyName2}")
        showEditStockPairDialog(pair)
    }

    private fun setupAddButton(): Unit {
        binding.addPairButton.setOnClickListener {
            showAddStockPairDialog()
        }
    }

    /**
     * Shows a dialog for adding a new stock pair.
     * Handles user input validation and API calls for stock information.
     */
    internal fun showAddStockPairDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_stock_pair, null)
        val ticker1Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker1Input)
        val ticker2Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker2Input)
        val priceDifferenceInput = dialogView.findViewById<TextInputEditText>(R.id.priceDifferenceInput)
        val notifyWhenEqualCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.notifyWhenEqualCheckbox)

        // Set up adapters
        val adapter1 = createStockAdapter()
        val adapter2 = createStockAdapter()
        ticker1Input.setAdapter(adapter1)
        ticker2Input.setAdapter(adapter2)

        // Set up search functionality with separate view models
        setupStockSearch(ticker1Input, adapter1, stockSearchViewModel1)
        setupStockSearch(ticker2Input, adapter2, stockSearchViewModel2)

        // Set up item click listeners
        ticker1Input.setOnItemClickListener { _, _, position, _ ->
            selectedStock1 = adapter1.getItem(position)
            Log.d(TAG, "Selected stock 1: $selectedStock1")
        }

        ticker2Input.setOnItemClickListener { _, _, position, _ ->
            selectedStock2 = adapter2.getItem(position)
            Log.d(TAG, "Selected stock 2: $selectedStock2")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Stock Pair")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val priceDifferenceStr = priceDifferenceInput.text.toString()
                val notifyWhenEqual = notifyWhenEqualCheckbox.isChecked

                if (selectedStock1 != null && selectedStock2 != null && priceDifferenceStr.isNotEmpty()) {
                    val priceDifference = priceDifferenceStr.toDoubleOrNull() ?: 0.0
                    lifecycleScope.launch {
                        try {
                            binding.progressBar.visibility = View.VISIBLE
                            
                            val stockPair = StockPair(
                                ticker1 = selectedStock1!!.symbol,
                                ticker2 = selectedStock2!!.symbol,
                                companyName1 = selectedStock1!!.name,
                                companyName2 = selectedStock2!!.name,
                                priceDifference = priceDifference,
                                notifyWhenEqual = notifyWhenEqual
                            )
                            
                            viewModel.addStockPair(stockPair)
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Stock pair added successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Failed to add stock pair: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Please select both stocks and enter price difference", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Creates an adapter for displaying stock search results.
     * The adapter handles both the input field display and dropdown items.
     *
     * @return ArrayAdapter<StockSearchResult> configured for displaying stock search results
     */
    internal fun createStockAdapter(): ArrayAdapter<StockSearchResult> {
        return object : ArrayAdapter<StockSearchResult>(
            this,
            R.layout.dropdown_item,
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
                    .inflate(R.layout.dropdown_item, parent, false)
                
                val item: StockSearchResult? = getItem(position)
                if (item != null) {
                    (view as TextView).text = "${item.symbol} - ${item.name}"
                }
                
                return view
            }

            override fun getFilter(): Filter {
                return object : Filter() {
                    @Suppress("UNCHECKED_CAST")
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val filterResults: FilterResults = FilterResults()
                        filterResults.values = mutableListOf<StockSearchResult>()
                        filterResults.count = 0
                        return filterResults
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?): Unit {
                        // Do nothing - we handle filtering through the ViewModel
                    }
                }
            }
        }
    }

    /**
     * Sets up the stock search functionality for an input field.
     * Configures the input field with debounced search, dropdown display,
     * and result handling through the ViewModel.
     *
     * @param input The AutoCompleteTextView to set up search for
     * @param adapter The adapter to display search results
     * @param viewModel The ViewModel handling the search logic
     */
    internal fun setupStockSearch(
        input: MaterialAutoCompleteTextView, 
        adapter: ArrayAdapter<StockSearchResult>,
        viewModel: StockSearchViewModel
    ) {
        Log.d(TAG, "Setting up search for input: ${input.id}")
        
        // Ensure the input is set up correctly
        input.threshold = 2  // Start showing suggestions after 2 characters
        input.setAdapter(adapter)  // Make sure adapter is set
        
        // Create a separate coroutine scope for this input
        lifecycleScope.launch {
            viewModel.searchState
                .collect { state ->
                    Log.d(TAG, "Search state changed for input ${input.id}: $state")
                    when (state) {
                        is SearchState.Loading -> {
                            Log.d(TAG, "Loading state for input ${input.id}")
                        }
                        is SearchState.Success -> {
                            Log.d(TAG, "Success state for input ${input.id}, results: ${state.results}")
                            adapter.clear()
                            adapter.addAll(state.results)
                            adapter.notifyDataSetChanged()
                            
                            if (state.results.isNotEmpty() && input.text.isNotEmpty()) {
                                input.post {
                                    if (input.hasFocus()) {
                                        Log.d(TAG, "Showing dropdown for input ${input.id}")
                                        input.showDropDown()
                                    }
                                }
                            }
                        }
                        is SearchState.Error -> {
                            Log.e(TAG, "Error state for input ${input.id}: ${state.message}")
                            adapter.clear()
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
        }

        var textChangeJob: Job? = null
        
        input.doAfterTextChanged { text ->
            Log.d(TAG, "Text changed in input ${input.id}: $text")
            textChangeJob?.cancel()
            
            if (text.isNullOrEmpty()) {
                Log.d(TAG, "Clearing adapter for input ${input.id}")
                adapter.clear()
                adapter.notifyDataSetChanged()
                input.dismissDropDown()
                return@doAfterTextChanged
            }
            
            textChangeJob = lifecycleScope.launch {
                delay(300) // Debounce time
                Log.d(TAG, "Searching for: $text")
                viewModel.search(text.toString())
            }
        }

        input.setOnFocusChangeListener { _, hasFocus ->
            Log.d(TAG, "Focus changed for input ${input.id}, hasFocus: $hasFocus")
            if (hasFocus && input.text.isNotEmpty() && adapter.count > 0) {
                Log.d(TAG, "Showing dropdown on focus gain")
                input.post { input.showDropDown() }
            }
        }
    }

    /**
     * Shows a dialog for editing an existing stock pair.
     * Handles validation and updates through the ViewModel.
     *
     * @param pair The StockPair to edit
     */
    private fun showEditStockPairDialog(pair: StockPair) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_stock_pair, null)
        val ticker1Input = dialogView.findViewById<EditText>(R.id.ticker1Input).apply { setText(pair.ticker1) }
        val ticker2Input = dialogView.findViewById<EditText>(R.id.ticker2Input).apply { setText(pair.ticker2) }
        val priceDifferenceInput = dialogView.findViewById<EditText>(R.id.priceDifferenceInput).apply { setText(pair.priceDifference.toString()) }
        val notifyWhenEqualCheckbox = dialogView.findViewById<CheckBox>(R.id.notifyWhenEqualCheckbox).apply { isChecked = pair.notifyWhenEqual }

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Stock Pair")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val ticker1 = ticker1Input.text.toString().trim().uppercase()
                val ticker2 = ticker2Input.text.toString().trim().uppercase()
                val priceDifferenceStr = priceDifferenceInput.text.toString()
                val notifyWhenEqual = notifyWhenEqualCheckbox.isChecked

                if (ticker1.isNotEmpty() && ticker2.isNotEmpty() && priceDifferenceStr.isNotEmpty()) {
                    val priceDifference = priceDifferenceStr.toDoubleOrNull() ?: 0.0
                    lifecycleScope.launch {
                        try {
                            binding.progressBar.visibility = View.VISIBLE
                            
                            // Parallel fetch of company names and prices if tickers changed
                            val (companyName1, companyName2, price1, price2) = withContext(Dispatchers.IO) {
                                coroutineScope {
                                    val ticker1Changed = ticker1 != pair.ticker1
                                    val ticker2Changed = ticker2 != pair.ticker2
                                    
                                    val companyName1Deferred = if (ticker1Changed) {
                                        async { YahooFinanceService.getCompanyName(ticker1) }
                                    } else null
                                    
                                    val companyName2Deferred = if (ticker2Changed) {
                                        async { YahooFinanceService.getCompanyName(ticker2) }
                                    } else null
                                    
                                    val price1Deferred = if (ticker1Changed) {
                                        async { YahooFinanceService.getStockPrice(ticker1) }
                                    } else null
                                    
                                    val price2Deferred = if (ticker2Changed) {
                                        async { YahooFinanceService.getStockPrice(ticker2) }
                                    } else null

                                    val companyName1Result = companyName1Deferred?.await() ?: pair.companyName1
                                    val companyName2Result = companyName2Deferred?.await() ?: pair.companyName2
                                    val price1Result = price1Deferred?.await() ?: pair.currentPrice1
                                    val price2Result = price2Deferred?.await() ?: pair.currentPrice2

                                    if (ticker1Changed && !ticker1.contains(".ST")) {
                                        throw Exception("Could not find stock on Stockholm Stock Exchange: $ticker1")
                                    }
                                    if (ticker2Changed && !ticker2.contains(".ST")) {
                                        throw Exception("Could not find stock on Stockholm Stock Exchange: $ticker2")
                                    }

                                    Quadruple(companyName1Result, companyName2Result, price1Result, price2Result)
                                }
                            }
                            
                            // Create updated pair with current prices
                            val updatedPair = pair.copy(
                                ticker1 = ticker1,
                                ticker2 = ticker2,
                                companyName1 = companyName1,
                                companyName2 = companyName2,
                                priceDifference = priceDifference,
                                notifyWhenEqual = notifyWhenEqual
                            ).withCurrentPrices(price1, price2)
                            
                            viewModel.updateStockPair(updatedPair)
                            // Immediately refresh all prices after update
                            viewModel.refreshStockPairs()
                            updateLastUpdateTime()
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Stock pair updated successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Failed to update stock pair: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshPrices()
        }
    }

    fun refreshPrices() {
        lifecycleScope.launch {
            try {
                viewModel.refreshStockPairs()
                updateLastUpdateTime()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to refresh: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun requestNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Shows a confirmation dialog for deleting a stock pair.
     * Handles the deletion through the ViewModel if confirmed.
     *
     * @param pair The StockPair to delete
     */
    private fun showDeleteConfirmationDialog(pair: StockPair) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Stock Pair")
            .setMessage("Are you sure you want to delete the pair ${pair.getDisplayName()}?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        viewModel.deleteStockPair(pair)
                        Toast.makeText(this@MainActivity, "Stock pair deleted successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Failed to delete stock pair: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        /** Tag for logging purposes */
        private const val TAG = "MainActivity"
        /** Interval for automatic price refresh in milliseconds */
        private const val AUTO_REFRESH_INTERVAL = 60000L // 1 minute
        /** Format pattern for time display */
        private const val TIME_FORMAT = "HH:mm:ss"
    }
} 