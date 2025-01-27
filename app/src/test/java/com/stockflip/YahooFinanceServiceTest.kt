package com.stockflip

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

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
} 