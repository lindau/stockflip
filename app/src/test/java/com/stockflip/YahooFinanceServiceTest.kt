package com.stockflip

import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

@Ignore("Live network smoke tests; run manually when needed.")
class YahooFinanceServiceTest {
    
    @Test
    fun `searchStocks should return results for valid Swedish stock`() = runBlocking {
        // Given
        val query = "volvo"
        
        // When
        val results = YahooFinanceService.searchStocks(query)
        
        // Then
        assertFalse("Search results should not be empty", results.isEmpty())
        assertTrue("Should contain Volvo B", results.any { it.symbol == "VOLV-B.ST" })
        println("Found ${results.size} results for '$query':")
        results.forEach { println("${it.symbol}: ${it.name}") }
    }
    
    @Test
    fun `searchStocks should return results for valid Swedish bank`() = runBlocking {
        // Given
        val query = "swedbank"
        
        // When
        val results = YahooFinanceService.searchStocks(query)
        
        // Then
        assertFalse("Search results should not be empty", results.isEmpty())
        assertTrue("Should contain Swedbank A", results.any { it.symbol == "SWED-A.ST" })
        println("Found ${results.size} results for '$query':")
        results.forEach { println("${it.symbol}: ${it.name}") }
    }
    
    @Test
    fun `searchStocks should handle empty query`() = runBlocking {
        // Given
        val query = ""
        
        // When
        val results = YahooFinanceService.searchStocks(query)
        
        // Then
        assertTrue("Empty query should return empty results", results.isEmpty())
    }
    
    @Test
    fun `searchStocks should handle short query`() = runBlocking {
        // Given
        val query = "a"
        
        // When
        val results = YahooFinanceService.searchStocks(query)
        
        // Then
        assertTrue("Short query should return empty results", results.isEmpty())
    }
    
    @Test
    fun `searchStocks should filter out non-stock items`() = runBlocking {
        // Given
        val query = "ericsson"
        
        // When
        val results = YahooFinanceService.searchStocks(query)
        
        // Then
        assertFalse("Search results should not be empty", results.isEmpty())
        assertTrue("All results should be stocks", results.all { 
            !it.symbol.contains("^") && 
            !it.symbol.contains("=") && 
            !it.name.contains("Fund", ignoreCase = true) 
        })
        println("Found ${results.size} results for '$query':")
        results.forEach { println("${it.symbol}: ${it.name}") }
    }
    
    @Test
    fun `getATH should return valid ATH for Swedish stock`() = runBlocking {
        // Given
        val symbol = "VOLV-B.ST"
        
        // When
        val ath = YahooFinanceService.getATH(symbol)
        
        // Then
        assertNotNull("ATH should not be null for $symbol", ath)
        assertTrue("ATH should be greater than 0", ath!! > 0.0)
        println("ATH for $symbol: $ath")
    }
    
    @Test
    fun `getATH should return valid ATH for another Swedish stock`() = runBlocking {
        // Given
        val symbol = "ASSA-B.ST"
        
        // When
        val ath = YahooFinanceService.getATH(symbol)
        
        // Then
        assertNotNull("ATH should not be null for $symbol", ath)
        assertTrue("ATH should be greater than 0", ath!! > 0.0)
        println("ATH for $symbol: $ath")
    }
    
    @Test
    fun `getKeyMetric should return PE ratio for Swedish stock`() = runBlocking {
        // Given
        val symbol = "VOLV-B.ST"
        val metricType = WatchType.MetricType.PE_RATIO
        
        // When
        val peRatio = YahooFinanceService.getKeyMetric(symbol, metricType)
        
        // Then
        assertNotNull("P/E ratio should not be null for $symbol", peRatio)
        assertTrue("P/E ratio should be greater than 0", peRatio!! > 0.0)
        println("P/E ratio for $symbol: $peRatio")
    }
    
    @Test
    fun `getKeyMetric should return PS ratio for Swedish stock`() = runBlocking {
        // Given
        val symbol = "VOLV-B.ST"
        val metricType = WatchType.MetricType.PS_RATIO
        
        // When
        val psRatio = YahooFinanceService.getKeyMetric(symbol, metricType)
        
        // Then
        // P/S ratio might be null for some stocks, so we just check if it's valid when present
        if (psRatio != null) {
            assertTrue("P/S ratio should be greater than 0", psRatio > 0.0)
            println("P/S ratio for $symbol: $psRatio")
        } else {
            println("P/S ratio not available for $symbol")
        }
    }
    
    @Test
    fun `getKeyMetric should return dividend yield for Swedish stock`() = runBlocking {
        // Given
        val symbol = "VOLV-B.ST"
        val metricType = WatchType.MetricType.DIVIDEND_YIELD
        
        // When
        val dividendYield = YahooFinanceService.getKeyMetric(symbol, metricType)
        
        // Then
        // Dividend yield might be null for some stocks, so we just check if it's valid when present
        if (dividendYield != null) {
            assertTrue("Dividend yield should be greater than 0", dividendYield > 0.0)
            println("Dividend yield for $symbol: $dividendYield%")
        } else {
            println("Dividend yield not available for $symbol")
        }
    }
    
    @Test
    fun `getKeyMetric should return PE ratio for another Swedish stock`() = runBlocking {
        // Given
        val symbol = "ASSA-B.ST"
        val metricType = WatchType.MetricType.PE_RATIO
        
        // When
        val peRatio = YahooFinanceService.getKeyMetric(symbol, metricType)
        
        // Then
        assertNotNull("P/E ratio should not be null for $symbol", peRatio)
        assertTrue("P/E ratio should be greater than 0", peRatio!! > 0.0)
        println("P/E ratio for $symbol: $peRatio")
    }
    
    @Test
    fun `getKeyMetric should handle stocks without PE ratio gracefully`() = runBlocking {
        // Given - using a stock that might not have P/E ratio
        val symbol = "RVRC.ST"
        val metricType = WatchType.MetricType.PE_RATIO
        
        // When
        val peRatio = YahooFinanceService.getKeyMetric(symbol, metricType)
        
        // Then
        // Should not throw exception, but might return null
        if (peRatio != null) {
            assertTrue("P/E ratio should be greater than 0 if present", peRatio > 0.0)
            println("P/E ratio for $symbol: $peRatio")
        } else {
            println("P/E ratio not available for $symbol (this is acceptable)")
        }
    }
    
    @Test
    fun `getATH and getKeyMetric should both work for the same stock`() = runBlocking {
        // Given
        val symbol = "VOLV-B.ST"
        
        // When
        val ath = YahooFinanceService.getATH(symbol)
        val peRatio = YahooFinanceService.getKeyMetric(symbol, WatchType.MetricType.PE_RATIO)
        
        // Then
        assertNotNull("ATH should not be null for $symbol", ath)
        assertTrue("ATH should be greater than 0", ath!! > 0.0)
        
        // Key metric comes from Yahoo where available
        if (peRatio != null) {
            assertTrue("P/E ratio should be greater than 0", peRatio > 0.0)
            println("For $symbol:")
            println("  ATH: $ath (from Yahoo Finance)")
            println("  P/E ratio: $peRatio")
        } else {
            println("For $symbol:")
            println("  ATH: $ath (from Yahoo Finance)")
            println("  P/E ratio: Not available")
        }
    }
    
    @Test
    fun `getKeyMetric should return Yahoo-backed metric when available`() = runBlocking {
        // Given
        val symbol = "EVO.ST"
        val metricType = WatchType.MetricType.PE_RATIO
        
        // When
        val peRatio = YahooFinanceService.getKeyMetric(symbol, metricType)
        
        // Then
        // Should get a value when Yahoo exposes the metric, otherwise null
        if (peRatio != null) {
            assertTrue("P/E ratio should be greater than 0", peRatio > 0.0)
            println("✓ Successfully got P/E ratio for $symbol: $peRatio")
        } else {
            println("⚠ P/E ratio not available for $symbol")
        }
    }
} 
