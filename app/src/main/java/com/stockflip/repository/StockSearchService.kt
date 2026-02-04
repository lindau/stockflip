package com.stockflip.repository

import com.stockflip.StockSearchResult
import com.stockflip.YahooFinanceService

interface StockSearchService {
    suspend fun searchStocks(query: String, includeCrypto: Boolean): List<StockSearchResult>
}

class YahooStockSearchService : StockSearchService {
    override suspend fun searchStocks(query: String, includeCrypto: Boolean): List<StockSearchResult> {
        return YahooFinanceService.searchStocks(query, includeCrypto)
    }
}

