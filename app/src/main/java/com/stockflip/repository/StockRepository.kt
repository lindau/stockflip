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
class StockRepository(
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val cacheTTL: Long = TimeUnit.MINUTES.toMillis(5)
) {
    private val TAG = "StockRepository"
    private val cache = mutableMapOf<String, CacheEntry>()

    /**
     * Searches for stocks based on the provided query.
     * Handles both ticker and company name searches.
     *
     * @param query The search query (ticker or company name)
     * @param includeCrypto Whether to include cryptocurrency results (default: true)
     * @return Flow of SearchState representing the search progress and results
     */
    suspend fun searchStocks(query: String, includeCrypto: Boolean = true): Flow<SearchState> = flow {
        try {
            emit(SearchState.Loading)
            Log.d(TAG, "Starting stock search for query: $query (includeCrypto: $includeCrypto)")
            
            if (query.length < 2) {
                Log.d(TAG, "Query too short, returning empty list")
                emit(SearchState.Success(emptyList()))
                return@flow
            }

            cleanExpiredCache()
            val cacheKey = "${query.trim().lowercase()}_$includeCrypto"
            val cached = cache[cacheKey]
            if (cached != null && !isCacheExpired(cached.timestamp)) {
                Log.d(TAG, "Returning cached results for query: $query")
                emit(SearchState.Success(cached.results))
                return@flow
            }
            
            // Prepare query - handle both ticker and name searches
            val searchQuery = when {
                query.contains(".ST", ignoreCase = true) -> query.uppercase()
                query.contains("-", ignoreCase = true) && !query.contains("-USD") && !query.contains("-EUR") -> "${query.uppercase()}.ST"
                query.all { it.isLetterOrDigit() } -> query.uppercase() // Handle pure ticker searches
                else -> query
            }
            
            Log.d(TAG, "Modified search query: $searchQuery")
            
            // Perform search
            val results = YahooFinanceService.searchStocks(searchQuery, includeCrypto)
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

            cache[cacheKey] = CacheEntry(sortedResults, timeProvider())
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
        val currentTime = timeProvider()
        return currentTime - timestamp > cacheTTL
    }

    fun clearCache() {
        cache.clear()
    }

    // Periodically clean old cache entries
    fun cleanExpiredCache() {
        cache.entries.removeIf { (_, entry) ->
            isCacheExpired(entry.timestamp)
        }
    }
} 
