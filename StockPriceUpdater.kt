// Modified StockPriceUpdater.kt
class StockPriceUpdater(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = StockPairDatabase.getDatabase(applicationContext)
        val stockPairs = database.stockPairDao().getAllStockPairs().first()
        
        stockPairs.forEach { pair ->
            try {
                val priceA = YahooFinanceService.getStockPrice(pair.stockA)
                val priceB = YahooFinanceService.getStockPrice(pair.stockB)

                if (priceA != null && priceB != null) {
                    val priceDifference = abs(priceA - priceB)
                    if (priceDifference >= pair.priceDifference) {
                        createNotification(pair, priceA, priceB, "Arbitrage Opportunity")
                    } else if (pair.notifyWhenEqual && priceDifference == 0.0) {
                        createNotification(pair, priceA, priceB, "Prices Are Equal")
                    }
                }
            } catch (e: Exception) {
                Log.e("StockPriceUpdater", "Error updating prices for pair ${pair.id}", e)
            }
        }

        Result.success()
    }

    // ... rest of the implementation remains similar ...
}