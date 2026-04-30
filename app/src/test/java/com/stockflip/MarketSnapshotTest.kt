package com.stockflip

import org.junit.Assert.*
import org.junit.Test

/**
 * Tester för MarketSnapshot - data class för marknadsdata.
 */
class MarketSnapshotTest {

    @Test
    fun `forSingleStock should create snapshot with correct values`() {
        // When
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 100.0,
            previousClose = 95.0,
            week52High = 120.0,
            allTimeHigh = 150.0
        )

        // Then
        assertEquals(100.0, snapshot.lastPrice!!, 0.01)
        assertEquals(95.0, snapshot.previousCloseOrPriceB!!, 0.01)
        assertEquals(120.0, snapshot.week52High!!, 0.01)
        assertEquals(150.0, snapshot.allTimeHigh!!, 0.01)
    }

    @Test
    fun `forPair should create snapshot with correct values`() {
        // When
        val snapshot = MarketSnapshot.forPair(100.0, 90.0)

        // Then
        assertEquals(100.0, snapshot.lastPrice!!, 0.01)
        assertEquals(90.0, snapshot.previousCloseOrPriceB!!, 0.01)
        assertNull("week52High should be null for pairs", snapshot.week52High)
        assertNull("allTimeHigh should be null for pairs", snapshot.allTimeHigh)
    }

    @Test
    fun `getDailyChangePercent should calculate correct percentage increase`() {
        // Given
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 105.0,
            previousClose = 100.0,
            week52High = null
        )

        // When
        val changePercent = snapshot.getDailyChangePercent()

        // Then
        assertNotNull("Should calculate daily change percent", changePercent)
        assertEquals(5.0, changePercent!!, 0.01) // 5% increase
    }

    @Test
    fun `getDailyChangePercent should calculate correct percentage decrease`() {
        // Given
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 95.0,
            previousClose = 100.0,
            week52High = null
        )

        // When
        val changePercent = snapshot.getDailyChangePercent()

        // Then
        assertNotNull("Should calculate daily change percent", changePercent)
        assertEquals(-5.0, changePercent!!, 0.01) // -5% decrease
    }

    @Test
    fun `getDailyChangePercent should return null when lastPrice is null`() {
        // Given
        val snapshot = MarketSnapshot(
            lastPrice = null,
            previousCloseOrPriceB = 100.0,
            week52High = null
        )

        // When
        val changePercent = snapshot.getDailyChangePercent()

        // Then
        assertNull("Should return null when lastPrice is null", changePercent)
    }

    @Test
    fun `getDailyChangePercent should return null when previousClose is null`() {
        // Given
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 100.0,
            previousClose = null,
            week52High = null
        )

        // When
        val changePercent = snapshot.getDailyChangePercent()

        // Then
        assertNull("Should return null when previousClose is null", changePercent)
    }

    @Test
    fun `getDailyChangePercent should return null when previousClose is zero or negative`() {
        // Given
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 100.0,
            previousClose = 0.0,
            week52High = null
        )

        // When
        val changePercent = snapshot.getDailyChangePercent()

        // Then
        assertNull("Should return null when previousClose is zero", changePercent)
    }

    @Test
    fun `getDailyChangePercent should handle zero change`() {
        // Given
        val snapshot = MarketSnapshot.forSingleStock(
            lastPrice = 100.0,
            previousClose = 100.0,
            week52High = null
        )

        // When
        val changePercent = snapshot.getDailyChangePercent()

        // Then
        assertNotNull("Should calculate daily change percent even for zero change", changePercent)
        assertEquals(0.0, changePercent!!, 0.01)
    }
}
