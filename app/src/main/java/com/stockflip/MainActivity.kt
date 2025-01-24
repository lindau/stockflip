package com.stockflip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        val ticker1Input = dialogView.findViewById<EditText>(R.id.ticker1Input)
        val ticker2Input = dialogView.findViewById<EditText>(R.id.ticker2Input)
        val priceDifferenceInput = dialogView.findViewById<EditText>(R.id.priceDifferenceInput)
        val notifyWhenEqualCheckbox = dialogView.findViewById<CheckBox>(R.id.notifyWhenEqualCheckbox)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Stock Pair")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val ticker1 = ticker1Input.text.toString().trim().uppercase()
                val ticker2 = ticker2Input.text.toString().trim().uppercase()
                val priceDifferenceStr = priceDifferenceInput.text.toString()
                val notifyWhenEqual = notifyWhenEqualCheckbox.isChecked

                if (ticker1.isNotEmpty() && ticker2.isNotEmpty() && priceDifferenceStr.isNotEmpty()) {
                    val priceDifference = priceDifferenceStr.toDoubleOrNull() ?: 0.0
                    lifecycleScope.launch {
                        try {
                            binding.progressBar.visibility = View.VISIBLE
                            
                            // Fetch company names
                            val companyName1 = YahooFinanceService.getCompanyName(ticker1)
                                ?: throw Exception("Could not fetch company name for $ticker1")
                            val companyName2 = YahooFinanceService.getCompanyName(ticker2)
                                ?: throw Exception("Could not fetch company name for $ticker2")
                            
                            // Fetch initial prices
                            val price1 = YahooFinanceService.getStockPrice(ticker1)
                            val price2 = YahooFinanceService.getStockPrice(ticker2)
                            
                            // Create stock pair with initial prices
                            val stockPair = StockPair(
                                id = 0,
                                ticker1 = ticker1,
                                ticker2 = ticker2,
                                companyName1 = companyName1,
                                companyName2 = companyName2,
                                priceDifference = priceDifference,
                                notifyWhenEqual = notifyWhenEqual
                            ).withCurrentPrices(price1, price2)
                            
                            viewModel.addStockPair(stockPair)
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Stock pair added successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Failed to add stock pair: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
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