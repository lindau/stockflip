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
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required for alerts", Toast.LENGTH_LONG).show()
        }
    }

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

    private fun initializeUI() {
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

    private fun loadInitialData() {
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

    private fun startAutoRefresh() {
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

    private fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun updateLastUpdateTime() {
        val currentTime = SimpleDateFormat(TIME_FORMAT, Locale.getDefault()).format(Date())
        binding.lastUpdateTime.text = getString(R.string.last_updated, currentTime)
        Log.d(TAG, "Updated last update time to $currentTime")
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                Log.d(TAG, "Received UI state update: $state")
                handleUiState(state)
            }
        }
    }

    private fun handleUiState(state: UiState<List<StockPair>>) {
        when (state) {
            is UiState.Loading -> showLoading()
            is UiState.Success -> showSuccess(state.data)
            is UiState.Error -> showError(state.message)
        }
    }

    private fun showLoading() {
        Log.d(TAG, "Showing loading state")
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun showSuccess(data: List<StockPair>) {
        Log.d(TAG, "Received ${data.size} stock pairs")
        binding.progressBar.visibility = View.GONE
        (binding.stockPairsList.adapter as StockPairAdapter).submitList(data)
    }

    private fun showError(message: String) {
        Log.e(TAG, "Error state: $message")
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView")
        binding.stockPairsList.layoutManager = LinearLayoutManager(this)
        binding.stockPairsList.adapter = StockPairAdapter(
            onDeleteClick = { pair -> handleDeleteClick(pair) },
            onEditClick = { pair -> handleEditClick(pair) }
        )
    }

    private fun handleDeleteClick(pair: StockPair) {
        Log.d(TAG, "Delete clicked for pair: ${pair.companyName1} - ${pair.companyName2}")
        showDeleteConfirmationDialog(pair)
    }

    private fun handleEditClick(pair: StockPair) {
        Log.d(TAG, "Edit clicked for pair: ${pair.companyName1} - ${pair.companyName2}")
        showEditStockPairDialog(pair)
    }

    private fun setupAddButton() {
        binding.addPairButton.setOnClickListener {
            showAddStockPairDialog()
        }
    }

    private fun showAddStockPairDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_stock_pair, null)
        val originalTicker1Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker1Input)
        val ticker2Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker2Input)
        val priceDifferenceInput = dialogView.findViewById<TextInputEditText>(R.id.priceDifferenceInput)
        val notifyWhenEqualCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.notifyWhenEqualCheckbox)

        Log.d(TAG, "Setting up autocomplete for stock search")

        // Set up autocomplete adapters with custom layout
        val adapter1 = object : ArrayAdapter<StockSearchResult>(
            this,
            R.layout.dropdown_item,
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                Log.d(TAG, "Adapter1 getView called for position: $position")
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.dropdown_item, parent, false)
                
                val item = getItem(position)
                if (item != null) {
                    Log.d(TAG, "Adapter1 binding item: ${item.symbol}: ${item.name}")
                    (view as TextView).text = "${item.name} (${item.symbol})"
                }
                
                return view
            }

            override fun clear() {
                Log.d(TAG, "Adapter1 clear called")
                super.clear()
                notifyDataSetChanged()
            }

            override fun addAll(collection: Collection<StockSearchResult>) {
                Log.d(TAG, "Adapter1 addAll called with ${collection.size} items")
                super.addAll(collection)
                notifyDataSetChanged()
            }
        }
        
        val adapter2 = object : ArrayAdapter<StockSearchResult>(
            this,
            R.layout.dropdown_item,
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.dropdown_item, parent, false)
                
                val item = getItem(position)
                if (item != null) {
                    (view as TextView).text = "${item.name} (${item.symbol})"
                }
                
                return view
            }
        }.apply {
            setNotifyOnChange(true)
        }

        // Override showDropDown and dismissDropDown for better tracking
        class TrackedAutoCompleteTextView(context: Context) : MaterialAutoCompleteTextView(context) {
            override fun showDropDown() {
                if (adapter?.count ?: 0 > 0) {
                    Log.d(TAG, "About to show dropdown with ${adapter?.count} items")
                    super.showDropDown()
                    Log.d(TAG, "Dropdown shown with ${adapter?.count} items")
                } else {
                    Log.d(TAG, "Not showing dropdown - no items")
                }
            }

            override fun dismissDropDown() {
                if (isPopupShowing) {
                    Log.d(TAG, "About to dismiss dropdown")
                    super.dismissDropDown()
                    Log.d(TAG, "Dropdown dismissed")
                }
            }
        }

        // Create custom AutoCompleteTextView instance
        val ticker1Input = TrackedAutoCompleteTextView(this).apply {
            id = originalTicker1Input.id
            layoutParams = originalTicker1Input.layoutParams
            setAdapter(adapter1)
            threshold = 2
            dropDownHeight = (resources.displayMetrics.density * 300).toInt()
            dropDownVerticalOffset = (resources.displayMetrics.density * 8).toInt()
            setDropDownBackgroundResource(android.R.color.white)
            hint = originalTicker1Input.hint
        }

        // Replace the original AutoCompleteTextView with our tracked version
        (originalTicker1Input.parent as ViewGroup).apply {
            val index = indexOfChild(originalTicker1Input)
            removeView(originalTicker1Input)
            addView(ticker1Input, index)
        }
        
        ticker2Input.apply {
            setAdapter(adapter2)
            threshold = 2
            dropDownHeight = (resources.displayMetrics.density * 300).toInt() // 300dp
            dropDownVerticalOffset = (resources.displayMetrics.density * 8).toInt() // 8dp
            setDropDownBackgroundResource(android.R.color.white)
        }

        // Store selected results
        var selectedStock1: StockSearchResult? = null
        var selectedStock2: StockSearchResult? = null

        // Set up search listeners with debounce
        var searchJob1: Job? = null
        ticker1Input.doAfterTextChanged { text ->
            val query = text?.toString()?.trim() ?: ""
            Log.d(TAG, "Ticker1 text changed: '$query', length: ${query.length}")
            searchJob1?.cancel()
            selectedStock1 = null  // Reset selection when text changes
            
            if (query.length >= 2) {
                ticker1Input.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_popup_sync, 0)
                searchJob1 = lifecycleScope.launch(Dispatchers.Main + SupervisorJob()) {
                    try {
                        Log.d(TAG, "Starting search for: '$query'")
                        delay(100) // Reduced debounce delay further
                        
                        val results = withContext(Dispatchers.IO) {
                            YahooFinanceService.searchStocks(query)
                        }
                        Log.d(TAG, "Found ${results.size} results for query: '$query'")
                        ticker1Input.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                        
                        if (results.isNotEmpty() && ticker1Input.text.toString().trim().equals(query, ignoreCase = true)) {
                            withContext(Dispatchers.Main) {
                                adapter1.clear()
                                adapter1.addAll(results)
                                if (ticker1Input.hasFocus()) {
                                    ticker1Input.post {
                                        if (adapter1.count > 0) {
                                            ticker1Input.showDropDown()
                                        }
                                    }
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                adapter1.clear()
                                ticker1Input.dismissDropDown()
                            }
                        }
                    } catch (e: Exception) {
                        ticker1Input.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                        if (e is CancellationException) {
                            Log.d(TAG, "Search cancelled for: '$query'")
                        } else {
                            Log.e(TAG, "Error searching stocks: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Error searching stocks", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } else {
                Log.d(TAG, "Query too short, dismissing dropdown")
                ticker1Input.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                ticker1Input.dismissDropDown()
            }
        }

        var searchJob2: Job? = null
        ticker2Input.doAfterTextChanged { text ->
            val query = text?.toString()?.trim() ?: ""
            Log.d(TAG, "Ticker2 text changed: $query")
            searchJob2?.cancel()
            selectedStock2 = null  // Reset selection when text changes
            
            if (query.length >= 2) {
                ticker2Input.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_popup_sync, 0)
                searchJob2 = lifecycleScope.launch(Dispatchers.Main + SupervisorJob()) {
                    try {
                        Log.d(TAG, "Starting search for: $query")
                        delay(100) // Reduced debounce delay further
                        
                        val results = withContext(Dispatchers.IO) {
                            YahooFinanceService.searchStocks(query)
                        }
                        Log.d(TAG, "Found ${results.size} results for query: $query")
                        ticker2Input.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                        
                        withContext(Dispatchers.Main) {
                            adapter2.clear()
                            // Only show results if the query hasn't changed
                            if (ticker2Input.text.toString().trim().equals(query, ignoreCase = true)) {
                                if (results.isNotEmpty()) {
                                    adapter2.addAll(results)
                                    if (ticker2Input.hasFocus()) {
                                        ticker2Input.post {
                                            ticker2Input.showDropDown()
                                        }
                                    }
                                } else {
                                    ticker2Input.dismissDropDown()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        ticker2Input.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                        if (e is CancellationException) {
                            Log.d(TAG, "Search cancelled for: $query")
                        } else {
                            Log.e(TAG, "Error searching stocks: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Error searching stocks", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } else {
                ticker2Input.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                ticker2Input.dismissDropDown()
            }
        }

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
                            
                            // Create stock pair with company names from search results
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

                                    if (ticker1Changed && companyName1Result == null) {
                                        throw Exception("Could not fetch company name for $ticker1")
                                    }
                                    if (ticker2Changed && companyName2Result == null) {
                                        throw Exception("Could not fetch company name for $ticker2")
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

    companion object {
        private const val TAG = "MainActivity"
        private const val AUTO_REFRESH_INTERVAL = 60000L // 1 minute
        private const val TIME_FORMAT = "HH:mm:ss"
    }
} 