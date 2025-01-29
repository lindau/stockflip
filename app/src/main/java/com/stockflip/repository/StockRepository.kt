package com.stockflip.repository

import android.util.Log
import com.stockflip.StockSearchResult
import com.stockflip.YahooFinanceService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit

/**
 * Repository for handling stock-related operations.
 * Manages caching and provides a clean API for stock searches.
 */
class StockRepository {
    private val TAG = "StockRepository"
    private val cache = mutableMapOf<String, CacheEntry>()
    private val cacheTTL = TimeUnit.MINUTES.toMillis(5) // 5 minutes TTL

    /**
     * Searches for stocks based on the provided query.
     * Handles both ticker and company name searches.
     *
     * @param query The search query (ticker or company name)
     * @return Flow of SearchState representing the search progress and results
     */
    suspend fun searchStocks(query: String): Flow<SearchState> = flow {
        try {
            emit(SearchState.Loading)
            Log.d(TAG, "Starting stock search for query: $query")
            
            if (query.length < 2) {
                Log.d(TAG, "Query too short, returning empty list")
                emit(SearchState.Success(emptyList()))
                return@flow
            }
            
            // Prepare query - handle both ticker and name searches
            val searchQuery = when {
                query.contains(".ST", ignoreCase = true) -> query.uppercase()
                query.contains("-", ignoreCase = true) -> "${query.uppercase()}.ST"
                query.all { it.isLetterOrDigit() } -> query.uppercase() // Handle pure ticker searches
                else -> query
            }
            
            Log.d(TAG, "Modified search query: $searchQuery")
            
            // Perform search
            val results = YahooFinanceService.searchStocks(searchQuery)
            Log.d(TAG, "Received ${results.size} results from YahooFinanceService")
            
            // Enhanced sorting logic that handles both ticker and name searches
            val upperQuery = query.uppercase()
            val sortedResults = results.sortedWith(
                compareByDescending<StockSearchResult> { 
                    // First priority: Exact ticker matches
                    it.symbol == upperQuery || 
                    it.symbol == "$upperQuery.ST" || 
                    it.symbol.removeSuffix(".ST") == upperQuery ||
                    it.symbol.removeSuffix(".ST").replace("-", "") == upperQuery
                }
                .thenByDescending { it.isSwedish } // Second priority: Swedish stocks
                .thenBy { it.symbol } // Third priority: Alphabetical order
            )
            
            emit(SearchState.Success(sortedResults))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during stock search: ${e.message}")
            emit(SearchState.Error(e.message ?: "Unknown error", query))
        }
    }

    private data class CacheEntry(
        val results: List<StockSearchResult>,
        val timestamp: Long
    )

    private fun isCacheExpired(timestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - timestamp > cacheTTL
    }

    fun clearCache() {
        cache.clear()
    }

    // Periodically clean old cache entries
    fun cleanExpiredCache() {
        val currentTime = System.currentTimeMillis()
        cache.entries.removeIf { (_, entry) ->
            isCacheExpired(entry.timestamp)
        }
    }

    private suspend fun fetchStockPrice(ticker: String): Double? {
        return try {
            YahooFinanceService.getStockPrice(ticker)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching stock price for $ticker: ${e.message}")
            null
        }
    }
} 