package com.stockflip

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.widget.CheckBox
import android.content.Context
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import com.stockflip.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

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
    private lateinit var stockPriceAlarmManager: StockPriceAlarmManager
    private val priceUpdateReceiver = PriceUpdateReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        stockPriceAlarmManager = StockPriceAlarmManager(this)

        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
        setupFab()
        
        // Schedule periodic price checks
        stockPriceAlarmManager.scheduleStockPriceCheck(15) // Check every 15 minutes
    }

    override fun onResume() {
        super.onResume()
        
        // Register broadcast receiver
        try {
            registerReceiver(
                priceUpdateReceiver,
                PriceUpdateReceiver.createIntentFilter(),
                RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register price update receiver: ${e.message}")
        }
        
        // Refresh stock prices
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Refreshing stock prices on app open")
                viewModel.refreshStockPairs()
                updateLastUpdateTime()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh stock prices on app open", e)
                Toast.makeText(this@MainActivity, "Failed to refresh prices: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(priceUpdateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister price update receiver: ${e.message}")
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                Log.d(TAG, "Received UI state update: $state")
                when (state) {
                    is UiState.Loading -> {
                        Log.d(TAG, "Showing loading state")
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is UiState.Success -> {
                        Log.d(TAG, "Received ${state.data.size} stock pairs")
                        binding.progressBar.visibility = View.GONE
                        (binding.stockPairsList.adapter as StockPairAdapter).submitList(state.data)
                    }
                    is UiState.Error -> {
                        Log.e(TAG, "Error state: ${state.message}")
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Register broadcast receiver for price updates
        try {
            registerReceiver(
                priceUpdateReceiver,
                PriceUpdateReceiver.createIntentFilter(),
                RECEIVER_NOT_EXPORTED
            )
            Log.d(TAG, "Successfully registered price update receiver")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register price update receiver: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView")
        binding.stockPairsList.layoutManager = LinearLayoutManager(this)
        binding.stockPairsList.adapter = StockPairAdapter(
            onDeleteClick = { pair -> 
                Log.d(TAG, "Delete clicked for pair: ${pair.companyName1} - ${pair.companyName2}")
                showDeleteConfirmationDialog(pair) 
            },
            onEditClick = { pair -> 
                Log.d(TAG, "Edit clicked for pair: ${pair.companyName1} - ${pair.companyName2}")
                showEditStockPairDialog(pair) 
            }
        )
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
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
    }

    private fun updateLastUpdateTime() {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        binding.lastUpdateText.text = "Last updated: $currentTime"
    }

    private fun setupFab() {
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
            .setMessage("Are you sure you want to delete the pair ${pair.getDisplayPair()}?")
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
        val ticker1Input = dialogView.findViewById<EditText>(R.id.ticker1Input)
        val ticker2Input = dialogView.findViewById<EditText>(R.id.ticker2Input)
        val priceDifferenceInput = dialogView.findViewById<EditText>(R.id.priceDifferenceInput)
        val notifyWhenEqualCheckbox = dialogView.findViewById<CheckBox>(R.id.notifyWhenEqualCheckbox)

        // Populate fields with current data
        ticker1Input.setText(pair.ticker1)
        ticker2Input.setText(pair.ticker2)
        priceDifferenceInput.setText(pair.priceDifference.toString())
        notifyWhenEqualCheckbox.isChecked = pair.notifyWhenEqual

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
                            
                            // Only fetch new company names if tickers changed
                            val companyName1 = if (ticker1 != pair.ticker1) {
                                YahooFinanceService.getCompanyName(ticker1)
                                    ?: throw Exception("Could not fetch company name for $ticker1")
                            } else pair.companyName1
                            
                            val companyName2 = if (ticker2 != pair.ticker2) {
                                YahooFinanceService.getCompanyName(ticker2)
                                    ?: throw Exception("Could not fetch company name for $ticker2")
                            } else pair.companyName2

                            // Get current prices
                            val price1 = YahooFinanceService.getStockPrice(ticker1)
                            val price2 = YahooFinanceService.getStockPrice(ticker2)
                            
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

    companion object {
        private const val TAG = "MainActivity"
    }
} 