// Modified MainActivity.kt
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModels
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = StockPairDatabase.getDatabase(applicationContext)
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(database.stockPairDao(), database.stockWatchDao(), YahooFinanceService) as T
            }
        }
    }
    
    private lateinit var stockPairsList: RecyclerView
    private lateinit var addPairButton: Button
    private lateinit var addWatchButton: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var stockPairAdapter: StockPairAdapter
    private lateinit var stockWatchAdapter: StockWatchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stockPairsList = findViewById(R.id.stockPairsList)
        addPairButton = findViewById(R.id.addPairButton)
        addWatchButton = findViewById(R.id.addWatchButton)
        progressBar = findViewById(R.id.progressBar)

        setupRecyclerView()
        setupObservers()

        addPairButton.setOnClickListener {
            showAddPairDialog()
        }

        addWatchButton.setOnClickListener {
            showAddWatchDialog()
        }

        StockPriceUpdater.startPeriodicUpdate(applicationContext)
    }

    private fun setupRecyclerView() {
        stockPairAdapter = StockPairAdapter(emptyList()) { pair ->
            viewModel.deleteStockPair(pair)
        }
        
        stockWatchAdapter = StockWatchAdapter { watch ->
            viewModel.deleteStockWatch(watch)
        }

        val concatAdapter = ConcatAdapter(stockPairAdapter, stockWatchAdapter)
        stockPairsList.layoutManager = LinearLayoutManager(this)
        stockPairsList.adapter = concatAdapter

        // Add swipe to delete
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder) = false

            override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val bindingAdapter = viewHolder.bindingAdapter
                
                if (bindingAdapter == stockPairAdapter) {
                    val pair = stockPairAdapter.getCurrentList()[position]
                    viewModel.deleteStockPair(pair)
                } else if (bindingAdapter == stockWatchAdapter) {
                     // Need to account for potential offset if ConcatAdapter didn't handle it cleanly via bindingAdapterPosition relative to sub-adapter?
                     // bindingAdapterPosition gives position within the specific adapter (since 1.2.0)
                    val watch = stockWatchAdapter.currentList[position]
                    viewModel.deleteStockWatch(watch)
                }
            }
        }).attachToRecyclerView(stockPairsList)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Loading -> progressBar.visibility = View.VISIBLE
                    is UiState.Success -> {
                        progressBar.visibility = View.GONE
                        stockPairAdapter.submitList(state.pairs)
                        stockWatchAdapter.submitList(state.watches)
                    }
                    is UiState.Error -> {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showAddPairDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_pair, null)
        val stockA = dialogView.findViewById<EditText>(R.id.stockA)
        val stockB = dialogView.findViewById<EditText>(R.id.stockB)
        val priceDifference = dialogView.findViewById<EditText>(R.id.priceDifference)
        val notifyWhenEqual = dialogView.findViewById<CheckBox>(R.id.notifyWhenEqual)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Add Stock Pair")
            .setPositiveButton("Save") { _, _ ->
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    val result = viewModel.validateAndAddStockPair(
                        stockA.text.toString(),
                        stockB.text.toString(),
                        priceDifference.text.toString().toDoubleOrNull() ?: 0.0,
                        notifyWhenEqual.isChecked
                    )
                    
                    result.fold(
                        onSuccess = {
                            Toast.makeText(this@MainActivity, "Stock pair added", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddWatchDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_watch, null)
        val symbol = dialogView.findViewById<EditText>(R.id.watchSymbol)
        val criteriaTypeSpinner = dialogView.findViewById<Spinner>(R.id.criteriaTypeSpinner)
        val thresholdLayout = dialogView.findViewById<LinearLayout>(R.id.thresholdLayout)
        val thresholdValue = dialogView.findViewById<EditText>(R.id.thresholdValue)
        val comparisonRadioGroup = dialogView.findViewById<RadioGroup>(R.id.comparisonRadioGroup)
        val radioAbove = dialogView.findViewById<RadioButton>(R.id.radioAbove)
        val radioBelow = dialogView.findViewById<RadioButton>(R.id.radioBelow)
        val dropPercentageLayout = dialogView.findViewById<LinearLayout>(R.id.dropPercentageLayout)
        val dropPercentageValue = dialogView.findViewById<EditText>(R.id.dropPercentageValue)
        val notifyOnTrigger = dialogView.findViewById<CheckBox>(R.id.notifyOnTrigger)

        val criteriaTypes = listOf(
            "Prisnivå" to "PRICE_TARGET",
            "P/E-tal" to "PE_RATIO",
            "P/S-tal" to "PS_RATIO",
            "Fall från ATH" to "ATH_DROP",
            "Fall från dagshögsta" to "DAILY_HIGH_DROP"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            criteriaTypes.map { it.first }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        criteriaTypeSpinner.adapter = adapter

        criteriaTypeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedType = criteriaTypes[position].second
                when (selectedType) {
                    "PRICE_TARGET", "PE_RATIO", "PS_RATIO" -> {
                        thresholdLayout.visibility = View.VISIBLE
                        dropPercentageLayout.visibility = View.GONE
                    }
                    "ATH_DROP", "DAILY_HIGH_DROP" -> {
                        thresholdLayout.visibility = View.GONE
                        dropPercentageLayout.visibility = View.VISIBLE
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Add Stock Watch")
            .setPositiveButton("Save") { _, _ ->
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    val symbolText = symbol.text.toString().trim().uppercase()
                    if (symbolText.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Please enter a stock symbol", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        return@launch
                    }

                    val selectedType = criteriaTypes[criteriaTypeSpinner.selectedItemPosition].second
                    val watchCriteria = when (selectedType) {
                        "PRICE_TARGET" -> {
                            val threshold = thresholdValue.text.toString().toDoubleOrNull()
                            if (threshold == null || threshold <= 0) {
                                Toast.makeText(this@MainActivity, "Please enter a valid threshold value", Toast.LENGTH_SHORT).show()
                                progressBar.visibility = View.GONE
                                return@launch
                            }
                            val comparison = if (radioAbove.isChecked) ComparisonType.ABOVE else ComparisonType.BELOW
                            WatchCriteria.PriceTargetCriteria(threshold, comparison)
                        }
                        "PE_RATIO" -> {
                            val threshold = thresholdValue.text.toString().toDoubleOrNull()
                            if (threshold == null || threshold <= 0) {
                                Toast.makeText(this@MainActivity, "Please enter a valid threshold value", Toast.LENGTH_SHORT).show()
                                progressBar.visibility = View.GONE
                                return@launch
                            }
                            val comparison = if (radioAbove.isChecked) ComparisonType.ABOVE else ComparisonType.BELOW
                            WatchCriteria.PERatioCriteria(threshold, comparison)
                        }
                        "PS_RATIO" -> {
                            val threshold = thresholdValue.text.toString().toDoubleOrNull()
                            if (threshold == null || threshold <= 0) {
                                Toast.makeText(this@MainActivity, "Please enter a valid threshold value", Toast.LENGTH_SHORT).show()
                                progressBar.visibility = View.GONE
                                return@launch
                            }
                            val comparison = if (radioAbove.isChecked) ComparisonType.ABOVE else ComparisonType.BELOW
                            WatchCriteria.PSRatioCriteria(threshold, comparison)
                        }
                        "ATH_DROP" -> {
                            val dropPercentage = dropPercentageValue.text.toString().toDoubleOrNull()
                            if (dropPercentage == null || dropPercentage <= 0 || dropPercentage >= 100) {
                                Toast.makeText(this@MainActivity, "Please enter a valid drop percentage (0-100)", Toast.LENGTH_SHORT).show()
                                progressBar.visibility = View.GONE
                                return@launch
                            }
                            WatchCriteria.ATHDropCriteria(dropPercentage)
                        }
                        "DAILY_HIGH_DROP" -> {
                            val dropPercentage = dropPercentageValue.text.toString().toDoubleOrNull()
                            if (dropPercentage == null || dropPercentage <= 0 || dropPercentage >= 100) {
                                Toast.makeText(this@MainActivity, "Please enter a valid drop percentage (0-100)", Toast.LENGTH_SHORT).show()
                                progressBar.visibility = View.GONE
                                return@launch
                            }
                            WatchCriteria.DailyHighDropCriteria(dropPercentage)
                        }
                        else -> {
                            Toast.makeText(this@MainActivity, "Invalid criteria type", Toast.LENGTH_SHORT).show()
                            progressBar.visibility = View.GONE
                            return@launch
                        }
                    }

                    val result = viewModel.validateAndAddStockWatch(
                        symbolText,
                        watchCriteria,
                        notifyOnTrigger.isChecked
                    )
                    
                    result.fold(
                        onSuccess = {
                            Toast.makeText(this@MainActivity, "Watch added", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
                        }
                    )
                    progressBar.visibility = View.GONE
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}