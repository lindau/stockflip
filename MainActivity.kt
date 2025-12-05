// Modified MainActivity.kt
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import android.widget.ToggleButton

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
        val dropValue = dialogView.findViewById<EditText>(R.id.dropValue)
        val togglePercentage = dialogView.findViewById<ToggleButton>(R.id.togglePercentage)
        val notifyOnTrigger = dialogView.findViewById<CheckBox>(R.id.notifyOnTrigger)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Add Stock Watch")
            .setPositiveButton("Save") { _, _ ->
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    val result = viewModel.validateAndAddStockWatch(
                        symbol.text.toString(),
                        dropValue.text.toString().toDoubleOrNull() ?: 0.0,
                        togglePercentage.isChecked,
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
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}