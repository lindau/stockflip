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
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
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
                return MainViewModel(database.stockPairDao(), database.watchItemDao(), YahooFinanceService) as T
            }
        }
    }
    private lateinit var binding: ActivityMainBinding
    private val priceUpdateReceiver = PriceUpdateReceiver()
    private var refreshJob: Job? = null
    private var selectedStock1: StockSearchResult? = null
    private var selectedStock2: StockSearchResult? = null
    private var selectedStock: StockSearchResult? = null
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
    
    private val stockSearchViewModel: StockSearchViewModel by viewModels {
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
            // First load from database to show existing data quickly
            viewModel.loadWatchItems()
            // Then refresh to get latest prices and key metrics
            viewModel.refreshWatchItems()
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
                    viewModel.refreshWatchItems()
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
            viewModel.watchItemUiState.collect { state: UiState<List<WatchItem>> ->
                Log.d(TAG, "Received UI state update: $state")
                handleWatchItemUiState(state)
            }
        }
    }

    /**
     * Handles different UI states and updates the view accordingly.
     *
     * @param state The current UI state to handle
     */
    internal fun handleWatchItemUiState(state: UiState<List<WatchItem>>): Unit {
        when (state) {
            is UiState.Loading -> showLoading()
            is UiState.Success -> showWatchItemSuccess(state.data)
            is UiState.Error -> showError(state.message)
        }
    }

    private fun showLoading(): Unit {
        Log.d(TAG, "Showing loading state")
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun showWatchItemSuccess(data: List<WatchItem>): Unit {
        Log.d(TAG, "Received ${data.size} watch items")
        binding.progressBar.visibility = View.GONE
        (binding.stockPairsList.adapter as WatchItemAdapter).submitList(data)
    }

    private fun showError(message: String): Unit {
        Log.e(TAG, "Error state: $message")
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setupRecyclerView(): Unit {
        Log.d(TAG, "Setting up RecyclerView")
        binding.stockPairsList.layoutManager = LinearLayoutManager(this)
        binding.stockPairsList.adapter = WatchItemAdapter(
            onDeleteClick = { item: WatchItem -> handleDeleteClick(item) },
            onEditClick = { item: WatchItem -> handleEditClick(item) },
            onItemClick = { item: WatchItem -> showWatchItemDetailDialog(item) }
        )
    }

    private fun handleDeleteClick(item: WatchItem): Unit {
        Log.d(TAG, "Delete clicked for watch item: ${item.getDisplayName()}")
        showDeleteConfirmationDialog(item)
    }

    private fun handleEditClick(item: WatchItem): Unit {
        Log.d(TAG, "Edit clicked for watch item: ${item.getDisplayName()}")
        when (item.watchType) {
            is WatchType.PricePair -> showEditStockPairDialog(item)
            is WatchType.PriceTarget -> showEditPriceTargetDialog(item)
            is WatchType.KeyMetrics -> showEditKeyMetricsDialog(item)
            is WatchType.ATHBased -> showEditATHBasedDialog(item)
        }
    }

    private fun setupAddButton(): Unit {
        binding.addPairButton.setOnClickListener {
            showWatchTypeSelectionDialog()
        }
    }

    /**
     * Shows a dialog for selecting the type of watch to create.
     * This allows for easy extension with new watch types in the future.
     */
    private fun showWatchTypeSelectionDialog(): Unit {
        val watchTypes = listOf(
            "Aktiepar" to WatchType.PricePair(0.0, false),
            "Prisbevakning" to WatchType.PriceTarget(0.0, WatchType.PriceDirection.ABOVE),
            "Nyckeltal" to WatchType.KeyMetrics(WatchType.MetricType.PE_RATIO, 0.0, WatchType.PriceDirection.ABOVE),
            "ATH-bevakning" to WatchType.ATHBased(WatchType.DropType.PERCENTAGE, 0.0)
        )
        val watchTypeNames = watchTypes.map { it.first }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Välj bevakningstyp")
            .setItems(watchTypeNames) { _, which ->
                val selectedType = watchTypes[which].second
                when (selectedType) {
                    is WatchType.PricePair -> showAddStockPairDialog()
                    is WatchType.PriceTarget -> showAddPriceTargetDialog()
                    is WatchType.KeyMetrics -> showAddKeyMetricsDialog()
                    is WatchType.ATHBased -> showAddATHBasedDialog()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
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
                            
                            val watchItem = WatchItem(
                                watchType = WatchType.PricePair(priceDifference, notifyWhenEqual),
                                ticker1 = selectedStock1!!.symbol,
                                ticker2 = selectedStock2!!.symbol,
                                companyName1 = selectedStock1!!.name,
                                companyName2 = selectedStock2!!.name
                            )
                            
                            viewModel.addWatchItem(watchItem)
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Aktiepar tillagt", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Kunde inte lägga till aktiepar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Välj båda aktier och ange prissskillnad", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Shows a dialog for adding a new price target watch.
     * Handles user input validation and API calls for stock information.
     */
    private fun showAddPriceTargetDialog(): Unit {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput)
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput)

        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)

        // Set up search functionality
        setupStockSearch(tickerInput, adapter, stockSearchViewModel)

        // Set up item click listener
        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        // Set up direction dropdown
        val directions = arrayOf("Över", "Under")
        val directionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)
        directionInput.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Selected direction: ${directions[position]}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Lägg till prisbevakning")
            .setView(dialogView)
            .setPositiveButton("Lägg till") { _, _ ->
                val targetPriceStr = targetPriceInput.text.toString()
                val directionStr = directionInput.text.toString()

                if (selectedStock != null && targetPriceStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val targetPrice = targetPriceStr.toDoubleOrNull()
                    val direction = when (directionStr) {
                        "Över" -> WatchType.PriceDirection.ABOVE
                        "Under" -> WatchType.PriceDirection.BELOW
                        else -> WatchType.PriceDirection.ABOVE
                    }

                    if (targetPrice != null && targetPrice > 0) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE

                                val watchItem = WatchItem(
                                    watchType = WatchType.PriceTarget(targetPrice, direction),
                                    ticker = selectedStock!!.symbol,
                                    companyName = selectedStock!!.name
                                )

                                viewModel.addWatchItem(watchItem)
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Prisbevakning tillagd", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte lägga till prisbevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange ett giltigt målpris", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Välj aktie, ange målpris och riktning", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for adding a new key metrics watch.
     * Handles user input validation and API calls for stock information.
     */
    private fun showAddKeyMetricsDialog(): Unit {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_key_metrics, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.metricTypeInput)
        val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.targetValueInput)
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput)

        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)
        setupStockSearch(tickerInput, adapter, stockSearchViewModel)

        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        // Set up metric type dropdown
        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        val metricTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, metricTypes)
        metricTypeInput.setAdapter(metricTypeAdapter)
        metricTypeInput.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Selected metric type: ${metricTypes[position]}")
        }

        // Set up direction dropdown
        val directions = arrayOf("Över", "Under")
        val directionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)
        directionInput.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Selected direction: ${directions[position]}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Lägg till nyckeltalsbevakning")
            .setView(dialogView)
            .setPositiveButton("Lägg till") { _, _ ->
                val tickerStr = tickerInput.text.toString().trim()
                val metricTypeStr = metricTypeInput.text.toString()
                val targetValueStr = targetValueInput.text.toString()
                val directionStr = directionInput.text.toString()

                val finalTicker = selectedStock?.symbol ?: tickerStr

                if (finalTicker.isNotEmpty() && metricTypeStr.isNotEmpty() && targetValueStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val metricType = when (metricTypeStr) {
                        "P/E-tal" -> WatchType.MetricType.PE_RATIO
                        "P/S-tal" -> WatchType.MetricType.PS_RATIO
                        "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                        else -> null
                    }
                    val targetValue = targetValueStr.toDoubleOrNull()
                    val direction = when (directionStr) {
                        "Över" -> WatchType.PriceDirection.ABOVE
                        "Under" -> WatchType.PriceDirection.BELOW
                        else -> null
                    }

                    if (metricType != null && targetValue != null && targetValue > 0 && direction != null) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE

                                val watchItem = WatchItem(
                                    watchType = WatchType.KeyMetrics(metricType, targetValue, direction),
                                    ticker = finalTicker,
                                    companyName = selectedStock?.name
                                )

                                viewModel.addWatchItem(watchItem)
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Nyckeltalsbevakning tillagd", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte lägga till nyckeltalsbevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange giltiga värden för alla fält", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for adding a new ATH-based watch.
     * Handles user input validation and API calls for stock information.
     */
    private fun showAddATHBasedDialog(): Unit {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput)
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput)

        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)
        setupStockSearch(tickerInput, adapter, stockSearchViewModel)

        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        // Set up drop type dropdown
        val dropTypes = arrayOf("Procent", "Absolut (SEK)")
        val dropTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, dropTypes)
        dropTypeInput.setAdapter(dropTypeAdapter)
        dropTypeInput.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Selected drop type: ${dropTypes[position]}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Lägg till ATH-bevakning")
            .setView(dialogView)
            .setPositiveButton("Lägg till") { _, _ ->
                val tickerStr = tickerInput.text.toString().trim()
                val dropTypeStr = dropTypeInput.text.toString()
                val dropValueStr = dropValueInput.text.toString()

                val finalTicker = selectedStock?.symbol ?: tickerStr

                if (finalTicker.isNotEmpty() && dropTypeStr.isNotEmpty() && dropValueStr.isNotEmpty()) {
                    val dropType = when (dropTypeStr) {
                        "Procent" -> WatchType.DropType.PERCENTAGE
                        "Absolut (SEK)" -> WatchType.DropType.ABSOLUTE
                        else -> null
                    }
                    val dropValue = dropValueStr.toDoubleOrNull()

                    if (dropType != null && dropValue != null && dropValue > 0) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE

                                val watchItem = WatchItem(
                                    watchType = WatchType.ATHBased(dropType, dropValue),
                                    ticker = finalTicker,
                                    companyName = selectedStock?.name
                                )

                                viewModel.addWatchItem(watchItem)
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "ATH-bevakning tillagd", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte lägga till ATH-bevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange giltiga värden för alla fält", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
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
     * Shows a dialog for editing an existing watch item.
     * Handles validation and updates through the ViewModel.
     *
     * @param item The WatchItem to edit
     */
    private fun showEditStockPairDialog(item: WatchItem) {
        if (item.watchType !is WatchType.PricePair) return
        val pricePair = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_stock_pair, null)
        val ticker1Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker1Input).apply { setText(item.ticker1) }
        val ticker2Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker2Input).apply { setText(item.ticker2) }
        val priceDifferenceInput = dialogView.findViewById<TextInputEditText>(R.id.priceDifferenceInput).apply { setText(pricePair.priceDifference.toString()) }
        val notifyWhenEqualCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.notifyWhenEqualCheckbox).apply { isChecked = pricePair.notifyWhenEqual }

        // Set up adapters
        val adapter1 = createStockAdapter()
        val adapter2 = createStockAdapter()
        ticker1Input.setAdapter(adapter1)
        ticker2Input.setAdapter(adapter2)

        // Set up search functionality
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
            .setTitle("Redigera aktiepar")
            .setView(dialogView)
            .setPositiveButton("Uppdatera") { _, _ ->
                val ticker1Str = ticker1Input.text.toString().trim()
                val ticker2Str = ticker2Input.text.toString().trim()
                val priceDifferenceStr = priceDifferenceInput.text.toString()
                val notifyWhenEqual = notifyWhenEqualCheckbox.isChecked

                val finalTicker1 = selectedStock1?.symbol ?: ticker1Str
                val finalTicker2 = selectedStock2?.symbol ?: ticker2Str

                if (finalTicker1.isNotEmpty() && finalTicker2.isNotEmpty() && priceDifferenceStr.isNotEmpty()) {
                    val priceDifference = priceDifferenceStr.toDoubleOrNull() ?: 0.0
                    lifecycleScope.launch {
                        try {
                            binding.progressBar.visibility = View.VISIBLE
                            
                            val updatedItem = item.copy(
                                watchType = WatchType.PricePair(priceDifference, notifyWhenEqual),
                                ticker1 = finalTicker1,
                                ticker2 = finalTicker2,
                                companyName1 = selectedStock1?.name ?: item.companyName1,
                                companyName2 = selectedStock2?.name ?: item.companyName2
                            )
                            
                            viewModel.updateWatchItem(updatedItem)
                            viewModel.refreshWatchItems()
                            updateLastUpdateTime()
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Aktiepar uppdaterat", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Kunde inte uppdatera aktiepar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for editing an existing price target watch.
     */
    private fun showEditPriceTargetDialog(item: WatchItem) {
        if (item.watchType !is WatchType.PriceTarget) return
        val priceTarget = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput).apply { setText(item.ticker) }
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput).apply { setText(priceTarget.targetPrice.toString()) }
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput).apply {
            setText(when (priceTarget.direction) {
                WatchType.PriceDirection.ABOVE -> "Över"
                WatchType.PriceDirection.BELOW -> "Under"
            })
        }

        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)
        setupStockSearch(tickerInput, adapter, stockSearchViewModel)

        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        // Set up direction dropdown
        val directions = arrayOf("Över", "Under")
        val directionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)
        directionInput.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Selected direction: ${directions[position]}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Redigera prisbevakning")
            .setView(dialogView)
            .setPositiveButton("Uppdatera") { _, _ ->
                val tickerStr = tickerInput.text.toString().trim()
                val targetPriceStr = targetPriceInput.text.toString()
                val directionStr = directionInput.text.toString()

                val finalTicker = selectedStock?.symbol ?: tickerStr

                if (finalTicker.isNotEmpty() && targetPriceStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val targetPrice = targetPriceStr.toDoubleOrNull()
                    val direction = when (directionStr) {
                        "Över" -> WatchType.PriceDirection.ABOVE
                        "Under" -> WatchType.PriceDirection.BELOW
                        else -> WatchType.PriceDirection.ABOVE
                    }

                    if (targetPrice != null && targetPrice > 0) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE

                                val updatedItem = item.copy(
                                    watchType = WatchType.PriceTarget(targetPrice, direction),
                                    ticker = finalTicker,
                                    companyName = selectedStock?.name ?: item.companyName
                                )

                                viewModel.updateWatchItem(updatedItem)
                                viewModel.refreshWatchItems()
                                updateLastUpdateTime()
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Prisbevakning uppdaterad", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte uppdatera prisbevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange ett giltigt målpris", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for editing an existing key metrics watch.
     */
    private fun showEditKeyMetricsDialog(item: WatchItem) {
        if (item.watchType !is WatchType.KeyMetrics) return
        val keyMetrics = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_key_metrics, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput).apply { setText(item.ticker) }
        val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.metricTypeInput).apply {
            setText(when (keyMetrics.metricType) {
                WatchType.MetricType.PE_RATIO -> "P/E-tal"
                WatchType.MetricType.PS_RATIO -> "P/S-tal"
                WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
            })
        }
        val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.targetValueInput).apply {
            setText(keyMetrics.targetValue.toString())
        }
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput).apply {
            setText(when (keyMetrics.direction) {
                WatchType.PriceDirection.ABOVE -> "Över"
                WatchType.PriceDirection.BELOW -> "Under"
            })
        }

        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)
        setupStockSearch(tickerInput, adapter, stockSearchViewModel)

        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        // Set up metric type dropdown
        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        val metricTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, metricTypes)
        metricTypeInput.setAdapter(metricTypeAdapter)
        metricTypeInput.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Selected metric type: ${metricTypes[position]}")
        }

        // Set up direction dropdown
        val directions = arrayOf("Över", "Under")
        val directionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)
        directionInput.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Selected direction: ${directions[position]}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Redigera nyckeltalsbevakning")
            .setView(dialogView)
            .setPositiveButton("Uppdatera") { _, _ ->
                val tickerStr = tickerInput.text.toString().trim()
                val metricTypeStr = metricTypeInput.text.toString()
                val targetValueStr = targetValueInput.text.toString()
                val directionStr = directionInput.text.toString()

                val finalTicker = selectedStock?.symbol ?: tickerStr

                if (finalTicker.isNotEmpty() && metricTypeStr.isNotEmpty() && targetValueStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val metricType = when (metricTypeStr) {
                        "P/E-tal" -> WatchType.MetricType.PE_RATIO
                        "P/S-tal" -> WatchType.MetricType.PS_RATIO
                        "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                        else -> null
                    }
                    val targetValue = targetValueStr.toDoubleOrNull()
                    val direction = when (directionStr) {
                        "Över" -> WatchType.PriceDirection.ABOVE
                        "Under" -> WatchType.PriceDirection.BELOW
                        else -> null
                    }

                    if (metricType != null && targetValue != null && targetValue > 0 && direction != null) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE

                                val updatedItem = item.copy(
                                    watchType = WatchType.KeyMetrics(metricType, targetValue, direction),
                                    ticker = finalTicker,
                                    companyName = selectedStock?.name ?: item.companyName
                                )

                                viewModel.updateWatchItem(updatedItem)
                                viewModel.refreshWatchItems()
                            updateLastUpdateTime()
                            binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Nyckeltalsbevakning uppdaterad", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte uppdatera nyckeltalsbevakning: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                        Toast.makeText(this, "Ange giltiga värden för alla fält", Toast.LENGTH_SHORT).show()
                }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
            }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for editing an existing ATH-based watch.
     */
    private fun showEditATHBasedDialog(item: WatchItem) {
        if (item.watchType !is WatchType.ATHBased) return
        val athBased = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput).apply { setText(item.ticker) }
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput).apply {
            setText(when (athBased.dropType) {
                WatchType.DropType.PERCENTAGE -> "Procent"
                WatchType.DropType.ABSOLUTE -> "Absolut (SEK)"
            })
        }
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput).apply {
            setText(athBased.dropValue.toString())
        }

        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)
        setupStockSearch(tickerInput, adapter, stockSearchViewModel)

        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        // Set up drop type dropdown
        val dropTypes = arrayOf("Procent", "Absolut (SEK)")
        val dropTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, dropTypes)
        dropTypeInput.setAdapter(dropTypeAdapter)
        dropTypeInput.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Selected drop type: ${dropTypes[position]}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Redigera ATH-bevakning")
            .setView(dialogView)
            .setPositiveButton("Uppdatera") { _, _ ->
                val tickerStr = tickerInput.text.toString().trim()
                val dropTypeStr = dropTypeInput.text.toString()
                val dropValueStr = dropValueInput.text.toString()

                val finalTicker = selectedStock?.symbol ?: tickerStr

                if (finalTicker.isNotEmpty() && dropTypeStr.isNotEmpty() && dropValueStr.isNotEmpty()) {
                    val dropType = when (dropTypeStr) {
                        "Procent" -> WatchType.DropType.PERCENTAGE
                        "Absolut (SEK)" -> WatchType.DropType.ABSOLUTE
                        else -> null
                    }
                    val dropValue = dropValueStr.toDoubleOrNull()

                    if (dropType != null && dropValue != null && dropValue > 0) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE

                                val updatedItem = item.copy(
                                    watchType = WatchType.ATHBased(dropType, dropValue),
                                    ticker = finalTicker,
                                    companyName = selectedStock?.name ?: item.companyName
                                )

                                viewModel.updateWatchItem(updatedItem)
                                viewModel.refreshWatchItems()
                                updateLastUpdateTime()
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "ATH-bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte uppdatera ATH-bevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange giltiga värden för alla fält", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a detailed view dialog for a watch item where the user can view and edit all values.
     */
    private fun showWatchItemDetailDialog(item: WatchItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_watch_item_detail, null)
        
        // Set watch type chip
        val watchTypeChip = dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.watchTypeChip)
        watchTypeChip.text = item.getWatchTypeDisplayName()

        when (item.watchType) {
            is WatchType.PricePair -> showPricePairDetail(dialogView, item)
            is WatchType.PriceTarget -> showPriceTargetDetail(dialogView, item)
            is WatchType.KeyMetrics -> showKeyMetricsDetail(dialogView, item)
            is WatchType.ATHBased -> showATHBasedDetail(dialogView, item)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Bevakningsdetaljer")
            .setView(dialogView)
            .setPositiveButton("Spara") { _, _ ->
                saveWatchItemChanges(dialogView, item)
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showPricePairDetail(dialogView: android.view.View, item: WatchItem) {
        val pricePairDetails = dialogView.findViewById<android.view.View>(R.id.pricePairDetails)
        pricePairDetails.visibility = android.view.View.VISIBLE
        
        val detailStock1Name = dialogView.findViewById<TextView>(R.id.detailStock1Name)
        val detailStock1Price = dialogView.findViewById<TextView>(R.id.detailStock1Price)
        val detailStock2Name = dialogView.findViewById<TextView>(R.id.detailStock2Name)
        val detailStock2Price = dialogView.findViewById<TextView>(R.id.detailStock2Price)
        val detailPriceDifference = dialogView.findViewById<TextInputEditText>(R.id.detailPriceDifference)
        val detailNotifyWhenEqual = dialogView.findViewById<MaterialCheckBox>(R.id.detailNotifyWhenEqual)

        val pricePair = item.watchType as WatchType.PricePair

        detailStock1Name.text = "${item.companyName1 ?: item.ticker1} (${item.ticker1})"
        detailStock1Price.text = item.formatPrice1()
        detailStock2Name.text = "${item.companyName2 ?: item.ticker2} (${item.ticker2})"
        detailStock2Price.text = item.formatPrice2()
        detailPriceDifference.setText(pricePair.priceDifference.toString())
        detailNotifyWhenEqual.isChecked = pricePair.notifyWhenEqual
    }

    private fun showPriceTargetDetail(dialogView: android.view.View, item: WatchItem) {
        val priceTargetDetails = dialogView.findViewById<android.view.View>(R.id.priceTargetDetails)
        priceTargetDetails.visibility = android.view.View.VISIBLE
        
        val detailStockName = dialogView.findViewById<TextView>(R.id.detailStockName)
        val detailCurrentPrice = dialogView.findViewById<TextView>(R.id.detailCurrentPrice)
        val detailTargetPrice = dialogView.findViewById<TextInputEditText>(R.id.detailTargetPrice)
        val detailDirection = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailDirection)

        val priceTarget = item.watchType as WatchType.PriceTarget

        detailStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
        detailCurrentPrice.text = item.formatPrice()
        detailTargetPrice.setText(priceTarget.targetPrice.toString())
        
        val directions = arrayOf("Över", "Under")
        val directionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, directions)
        detailDirection.setAdapter(directionAdapter)
        detailDirection.setText(when (priceTarget.direction) {
            WatchType.PriceDirection.ABOVE -> "Över"
            WatchType.PriceDirection.BELOW -> "Under"
        }, false)
    }

    private fun showKeyMetricsDetail(dialogView: android.view.View, item: WatchItem) {
        val keyMetricsDetails = dialogView.findViewById<android.view.View>(R.id.keyMetricsDetails)
        keyMetricsDetails.visibility = android.view.View.VISIBLE
        
        val detailKeyMetricsStockName = dialogView.findViewById<TextView>(R.id.detailKeyMetricsStockName)
        val detailCurrentMetricValue = dialogView.findViewById<TextView>(R.id.detailCurrentMetricValue)
        val detailMetricType = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailMetricType)
        val detailTargetValue = dialogView.findViewById<TextInputEditText>(R.id.detailTargetValue)
        val detailKeyMetricsDirection = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailKeyMetricsDirection)

        val keyMetrics = item.watchType as WatchType.KeyMetrics

        detailKeyMetricsStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
        
        val metricTypeName = when (keyMetrics.metricType) {
            WatchType.MetricType.PE_RATIO -> "P/E-tal"
            WatchType.MetricType.PS_RATIO -> "P/S-tal"
            WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
        }
        detailCurrentMetricValue.text = "$metricTypeName: ${item.formatMetricValue()}"
        
        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        val metricTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, metricTypes)
        detailMetricType.setAdapter(metricTypeAdapter)
        detailMetricType.setText(metricTypeName, false)
        
        detailTargetValue.setText(keyMetrics.targetValue.toString())
        
        val directions = arrayOf("Över", "Under")
        val directionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, directions)
        detailKeyMetricsDirection.setAdapter(directionAdapter)
        detailKeyMetricsDirection.setText(when (keyMetrics.direction) {
            WatchType.PriceDirection.ABOVE -> "Över"
            WatchType.PriceDirection.BELOW -> "Under"
        }, false)
    }

    private fun showATHBasedDetail(dialogView: android.view.View, item: WatchItem) {
        val athBasedDetails = dialogView.findViewById<android.view.View>(R.id.athBasedDetails)
        athBasedDetails.visibility = android.view.View.VISIBLE
        
        val detailATHStockName = dialogView.findViewById<TextView>(R.id.detailATHStockName)
        val detailATHInfo = dialogView.findViewById<TextView>(R.id.detailATHInfo)
        val detailATHDropType = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailATHDropType)
        val detailATHDropValue = dialogView.findViewById<TextInputEditText>(R.id.detailATHDropValue)

        val athBased = item.watchType as WatchType.ATHBased

        detailATHStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
        
        val athInfoText = if (item.currentATH > 0.0) {
            "ATH: ${priceFormat.format(item.currentATH)} SEK | Nedgång: ${item.formatATHDrop()}"
        } else {
            "ATH: Loading... | Nedgång: ${item.formatATHDrop()}"
        }
        detailATHInfo.text = athInfoText
        
        val dropTypes = arrayOf("Procent", "Absolut (SEK)")
        val dropTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, dropTypes)
        detailATHDropType.setAdapter(dropTypeAdapter)
        detailATHDropType.setText(when (athBased.dropType) {
            WatchType.DropType.PERCENTAGE -> "Procent"
            WatchType.DropType.ABSOLUTE -> "Absolut (SEK)"
        }, false)
        
        detailATHDropValue.setText(athBased.dropValue.toString())
    }

    private fun saveWatchItemChanges(dialogView: android.view.View, item: WatchItem) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val updatedItem = when (item.watchType) {
                    is WatchType.PricePair -> {
                        val priceDifferenceInput = dialogView.findViewById<TextInputEditText>(R.id.detailPriceDifference)
                        val notifyWhenEqualCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.detailNotifyWhenEqual)
                        
                        val priceDifference = priceDifferenceInput.text.toString().toDoubleOrNull() ?: 0.0
                        val notifyWhenEqual = notifyWhenEqualCheckbox.isChecked
                        
                        item.copy(
                            watchType = WatchType.PricePair(priceDifference, notifyWhenEqual)
                        )
                    }
                    is WatchType.PriceTarget -> {
                        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.detailTargetPrice)
                        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailDirection)
                        
                        val targetPrice = targetPriceInput.text.toString().toDoubleOrNull() ?: 0.0
                        val direction = when (directionInput.text.toString()) {
                            "Över" -> WatchType.PriceDirection.ABOVE
                            "Under" -> WatchType.PriceDirection.BELOW
                            else -> WatchType.PriceDirection.ABOVE
                        }
                        
                        item.copy(
                            watchType = WatchType.PriceTarget(targetPrice, direction)
                        )
                    }
                    is WatchType.KeyMetrics -> {
                        val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailMetricType)
                        val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.detailTargetValue)
                        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailKeyMetricsDirection)
                        
                        val metricType = when (metricTypeInput.text.toString()) {
                            "P/E-tal" -> WatchType.MetricType.PE_RATIO
                            "P/S-tal" -> WatchType.MetricType.PS_RATIO
                            "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                            else -> {
                                val keyMetrics = item.watchType as? WatchType.KeyMetrics
                                keyMetrics?.metricType ?: WatchType.MetricType.PE_RATIO
                            }
                        }
                        val targetValue = targetValueInput.text.toString().toDoubleOrNull() ?: 0.0
                        val direction = when (directionInput.text.toString()) {
                            "Över" -> WatchType.PriceDirection.ABOVE
                            "Under" -> WatchType.PriceDirection.BELOW
                            else -> WatchType.PriceDirection.ABOVE
                        }
                        
                        item.copy(
                            watchType = WatchType.KeyMetrics(metricType, targetValue, direction)
                        )
                    }
                    is WatchType.ATHBased -> {
                        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailATHDropType)
                        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.detailATHDropValue)
                        
                        val dropType = when (dropTypeInput.text.toString()) {
                            "Procent" -> WatchType.DropType.PERCENTAGE
                            "Absolut (SEK)" -> WatchType.DropType.ABSOLUTE
                            else -> {
                                val athBased = item.watchType as? WatchType.ATHBased
                                athBased?.dropType ?: WatchType.DropType.PERCENTAGE
                            }
                        }
                        val dropValue = dropValueInput.text.toString().toDoubleOrNull() ?: 0.0
                        
                        item.copy(
                            watchType = WatchType.ATHBased(dropType, dropValue)
                        )
                    }
                }

                viewModel.updateWatchItem(updatedItem)
                viewModel.refreshWatchItems()
                updateLastUpdateTime()
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Kunde inte uppdatera bevakning: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
                viewModel.refreshWatchItems()
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
     * Shows a confirmation dialog for deleting a watch item.
     * Handles the deletion through the ViewModel if confirmed.
     *
     * @param item The WatchItem to delete
     */
    private fun showDeleteConfirmationDialog(item: WatchItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Ta bort bevakning")
            .setMessage("Är du säker på att du vill ta bort bevakningen ${item.getDisplayName()}?")
            .setPositiveButton("Ta bort") { _, _ ->
                lifecycleScope.launch {
                    try {
                        viewModel.deleteWatchItem(item)
                        Toast.makeText(this@MainActivity, "Bevakning borttagen", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Kunde inte ta bort bevakning: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    companion object {
        /** Tag for logging purposes */
        private const val TAG = "MainActivity"
        /** Interval for automatic price refresh in milliseconds */
        private const val AUTO_REFRESH_INTERVAL = 60000L // 1 minute
        /** Format pattern for time display */
        private const val TIME_FORMAT = "HH:mm:ss"
        /** Price format with Swedish locale */
        private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))
    }
} 