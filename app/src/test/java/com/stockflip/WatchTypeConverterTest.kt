package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchTypeConverterTest {
    private val converter = WatchTypeConverter()

    @Test
    fun `roundtrip PricePair`() {
        val watchType = WatchType.PricePair(priceDifference = 12.5, notifyWhenEqual = true)
        val encoded = converter.fromWatchType(watchType)
        val decoded = converter.toWatchType(encoded)
        assertEquals(watchType, decoded)
    }

    @Test
    fun `roundtrip PriceTarget`() {
        val watchType = WatchType.PriceTarget(targetPrice = 120.0, direction = WatchType.PriceDirection.ABOVE)
        val encoded = converter.fromWatchType(watchType)
        val decoded = converter.toWatchType(encoded)
        assertEquals(watchType, decoded)
    }

    @Test
    fun `roundtrip KeyMetrics`() {
        val watchType = WatchType.KeyMetrics(
            metricType = WatchType.MetricType.PE_RATIO,
            targetValue = 15.0,
            direction = WatchType.PriceDirection.BELOW
        )
        val encoded = converter.fromWatchType(watchType)
        val decoded = converter.toWatchType(encoded)
        assertEquals(watchType, decoded)
    }

    @Test
    fun `roundtrip ATHBased`() {
        val watchType = WatchType.ATHBased(
            dropType = WatchType.DropType.PERCENTAGE,
            dropValue = 20.0
        )
        val encoded = converter.fromWatchType(watchType)
        val decoded = converter.toWatchType(encoded)
        assertEquals(watchType, decoded)
    }

    @Test
    fun `roundtrip PriceRange`() {
        val watchType = WatchType.PriceRange(minPrice = 90.0, maxPrice = 120.0)
        val encoded = converter.fromWatchType(watchType)
        val decoded = converter.toWatchType(encoded)
        assertEquals(watchType, decoded)
    }

    @Test
    fun `roundtrip DailyMove`() {
        val watchType = WatchType.DailyMove(
            percentThreshold = 4.0,
            direction = WatchType.DailyMoveDirection.BOTH
        )
        val encoded = converter.fromWatchType(watchType)
        val decoded = converter.toWatchType(encoded)
        assertEquals(watchType, decoded)
    }
}
