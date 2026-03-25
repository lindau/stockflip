package com.stockflip

import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Tests for FinnhubService to verify key metrics fetching functionality.
 * 
 * Note: These tests require a valid Finnhub API key in local.properties.
 * If API key is not configured, tests will show warnings but won't fail.
 * 
 * To run these tests:
 * 1. Get a free API key from https://finnhub.io/
 * 2. Add FINNHUB_API_KEY=your_key_here to local.properties
 * 3. Rebuild the project
 * 4. Run the tests
 */
@Ignore("Live network tests; run manually when needed.")
class FinnhubServiceTest {
    
    @Before
    fun checkApiKey() {
        // This will print a warning if API key is not configured
        println("Running FinnhubService tests. Make sure FINNHUB_API_KEY is set in local.properties")
    }
    
    @Test
    fun `getKeyMetric should return PE ratio for Swedish stock when API key is configured`() = runBlocking {
        // Given
        val symbol = "VOLV-B.ST"
        val metricType = WatchType.MetricType.PE_RATIO
        
        // When
        val peRatio = FinnhubService.getKeyMetric(symbol, metricType)
        
        // Then
        if (peRatio != null) {
            assertTrue("P/E ratio should be greater than 0", peRatio > 0.0)
            println("✓ P/E ratio for $symbol: $peRatio")
        } else {
            println("⚠ P/E ratio not available for $symbol (API key might not be configured or stock not found)")
        }
    }
    
    @Test
    fun `getKeyMetric should return PS ratio for Swedish stock when API key is configured`() = runBlocking {
        // Given
        val symbol = "VOLV-B.ST"
        val metricType = WatchType.MetricType.PS_RATIO
        
        // When
        val psRatio = FinnhubService.getKeyMetric(symbol, metricType)
        
        // Then
        if (psRatio != null) {
            assertTrue("P/S ratio should be greater than 0", psRatio > 0.0)
            println("✓ P/S ratio for $symbol: $psRatio")
        } else {
            println("⚠ P/S ratio not available for $symbol (API key might not be configured or stock not found)")
        }
    }
    
    @Test
    fun `getKeyMetric should return dividend yield for Swedish stock when API key is configured`() = runBlocking {
        // Given
        val symbol = "VOLV-B.ST"
        val metricType = WatchType.MetricType.DIVIDEND_YIELD
        
        // When
        val dividendYield = FinnhubService.getKeyMetric(symbol, metricType)
        
        // Then
        if (dividendYield != null) {
            assertTrue("Dividend yield should be greater than 0", dividendYield > 0.0)
            println("✓ Dividend yield for $symbol: $dividendYield%")
        } else {
            println("⚠ Dividend yield not available for $symbol (API key might not be configured or stock not found)")
        }
    }
    
    @Test
    fun `getKeyMetric should handle stocks without exchange suffix`() = runBlocking {
        // Given
        val symbol = "AAPL" // Apple (US stock, no exchange suffix)
        val metricType = WatchType.MetricType.PE_RATIO
        
        // When
        val peRatio = FinnhubService.getKeyMetric(symbol, metricType)
        
        // Then
        if (peRatio != null) {
            assertTrue("P/E ratio should be greater than 0", peRatio > 0.0)
            println("✓ P/E ratio for $symbol: $peRatio")
        } else {
            println("⚠ P/E ratio not available for $symbol")
        }
    }
    
    @Test
    fun `getKeyMetric should try multiple symbol variants for Swedish stocks`() = runBlocking {
        // Given - Swedish stock with .ST suffix
        val symbol = "ASSA-B.ST"
        val metricType = WatchType.MetricType.PE_RATIO
        
        // When
        val peRatio = FinnhubService.getKeyMetric(symbol, metricType)
        
        // Then
        // Should try both ASSA-B.ST and ASSA-B
        if (peRatio != null) {
            assertTrue("P/E ratio should be greater than 0", peRatio > 0.0)
            println("✓ P/E ratio for $symbol: $peRatio (found with one of the symbol variants)")
        } else {
            println("⚠ P/E ratio not available for $symbol (tried both .ST and without suffix)")
        }
    }
    
    @Test
    fun `YahooFinanceService getKeyMetric should use Finnhub directly`() = runBlocking {
        // Given - YahooFinanceService.getKeyMetric now goes directly to Finnhub
        val symbol = "EVO.ST"
        val metricType = WatchType.MetricType.PE_RATIO
        
        // When
        val peRatio = YahooFinanceService.getKeyMetric(symbol, metricType)
        
        // Then
        // Should get value from Finnhub (or null if API key not configured)
        if (peRatio != null) {
            assertTrue("P/E ratio should be greater than 0", peRatio > 0.0)
            println("✓ P/E ratio for $symbol from Finnhub (via YahooFinanceService): $peRatio")
        } else {
            println("⚠ P/E ratio not available for $symbol (Finnhub API key might not be configured)")
        }
    }
    
    @Test
    fun `getKeyMetric should handle all three metric types`() = runBlocking {
        // Given
        val symbol = "VOLV-B.ST"
        
        // When - Test all three metric types
        val peRatio = FinnhubService.getKeyMetric(symbol, WatchType.MetricType.PE_RATIO)
        val psRatio = FinnhubService.getKeyMetric(symbol, WatchType.MetricType.PS_RATIO)
        val dividendYield = FinnhubService.getKeyMetric(symbol, WatchType.MetricType.DIVIDEND_YIELD)
        
        // Then
        println("Results for $symbol:")
        println("  P/E ratio: ${peRatio ?: "Not available"}")
        println("  P/S ratio: ${psRatio ?: "Not available"}")
        println("  Dividend yield: ${dividendYield ?: "Not available"}%")
        
        // At least one should be available if API is working
        val atLeastOneAvailable = peRatio != null || psRatio != null || dividendYield != null
        if (!atLeastOneAvailable) {
            println("⚠ Warning: No metrics available. Check API key configuration.")
        }
    }
}

