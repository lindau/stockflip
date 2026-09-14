package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Test

class StringListConverterTest {
    private val converter = StringListConverter()

    @Test
    fun `round-trips a list of strings through JSON`() {
        val original = listOf("Stark tillväxt", "Bra värdering")

        val json = converter.fromList(original)
        val restored = converter.toList(json)

        assertEquals(original, restored)
    }

    @Test
    fun `empty and null lists both round-trip to an empty list`() {
        assertEquals(emptyList<String>(), converter.toList(converter.fromList(emptyList())))
        assertEquals(emptyList<String>(), converter.toList(converter.fromList(null)))
    }

    @Test
    fun `toList tolerates blank or malformed input instead of throwing`() {
        assertEquals(emptyList<String>(), converter.toList(null))
        assertEquals(emptyList<String>(), converter.toList(""))
        assertEquals(emptyList<String>(), converter.toList("not json"))
    }
}
