package com.stockflip.repository

import android.util.Log
import com.stockflip.StockSearchResult
import com.stockflip.YahooFinanceService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class StockRepository {
    private val TAG = "StockRepository"
    
    private data class CacheEntry(
        val results: List<StockSearchResult>,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val cacheTTL = TimeUnit.MINUTES.toMillis(5) // 5 minutes TTL
    
    fun searchStocks(query: String): Flow<SearchState> = flow {
        emit(SearchState.Loading)
        
        try {
            // Check cache first
            val cachedEntry = cache[query]
            if (cachedEntry != null && !isCacheExpired(cachedEntry.timestamp)) {
                Log.d(TAG, "Cache hit for query: $query")
                emit(SearchState.Success(cachedEntry.results))
                return@flow
            }
            
            // Perform search
            val results = YahooFinanceService.searchStocks(query)
            
            // Sort results (Swedish stocks first, then by relevance)
            val sortedResults = results.sortedWith(
                compareByDescending<StockSearchResult> { it.isSwedish }
                    .thenBy { it.symbol }
            )

            // Cache results
            cache[query] = CacheEntry(sortedResults, System.currentTimeMillis())
            
            emit(SearchState.Success(sortedResults))
        } catch (e: Exception) {
            Log.e(TAG, "Error searching stocks: ${e.message}", e)
            emit(SearchState.Error("Failed to search stocks: ${e.message}", query))
        }
    }.catch { e ->
        Log.e(TAG, "Error in search flow: ${e.message}", e)
        emit(SearchState.Error("An unexpected error occurred", ""))
    }

    private fun isCacheExpired(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp > cacheTTL
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
} 