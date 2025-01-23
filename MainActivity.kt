// Modified MainActivity.kt
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
    
    private lateinit var stockPairsList: RecyclerView
    private lateinit var addPairButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stockPairsList = findViewById(R.id.stockPairsList)
        addPairButton = findViewById(R.id.addPairButton)
        progressBar = findViewById(R.id.progressBar)

        setupRecyclerView()
        setupObservers

        addPairButton.setOnClickListener {
            showAddPairDialog()
        }

        StockPriceUpdater.startPeriodicUpdate(applicationContext)
    }

    private fun setupRecyclerView() {
        stockPairsList.layoutManager = LinearLayoutManager(this)
        stockPairsList.adapter = StockPairAdapter(emptyList()) { pair ->
            viewModel.deleteStockPair(pair)
        }

        // Add swipe to delete
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder) = false

            override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val pair = (stockPairsList.adapter as StockPairAdapter).getCurrentList()[position]
                viewModel.deleteStockPair(pair)
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
                        (stockPairsList.adapter as StockPairAdapter).submitList(state.pairs)
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
}