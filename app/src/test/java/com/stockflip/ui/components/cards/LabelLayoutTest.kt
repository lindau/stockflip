package com.stockflip.ui.components.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelLayoutTest {
    @Test
    fun farApartLabelsAreUnchanged() {
        val result = resolveLabelOffsets(listOf(10f, 100f), listOf(14f, 14f), 2f, 300f)
        assertEquals(listOf(10f, 100f), result)
    }

    @Test
    fun closeLabelsDoNotOverlap() {
        val result = resolveLabelOffsets(listOf(50f, 54f), listOf(14f, 14f), 2f, 300f)
        assertEquals(50f, result[0], 0.001f)
        assertTrue(result[1] >= result[0] + 14f + 2f)
    }

    @Test
    fun labelsNearBottomAreShiftedUpAndFit() {
        val result = resolveLabelOffsets(listOf(280f, 282f, 284f), listOf(14f, 14f, 14f), 2f, 300f)
        assertTrue(result.last() + 14f <= 300f + 0.001f)
        for (i in 1 until result.size) assertTrue(result[i] >= result[i - 1] + 16f - 0.001f)
    }
}
