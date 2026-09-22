package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateVersionComparatorTest {

    @Test
    fun `newer patch version is detected`() {
        assertTrue(isNewerVersion("1.2.93", "1.2.94"))
    }

    @Test
    fun `equal version is not newer`() {
        assertFalse(isNewerVersion("1.2.93", "1.2.93"))
    }

    @Test
    fun `older version is not newer`() {
        assertFalse(isNewerVersion("1.2.94", "1.2.93"))
    }

    @Test
    fun `strips leading v prefix from a tag`() {
        assertEquals("1.2.94", stripVersionPrefix("v1.2.94"))
        assertEquals("1.2.94", stripVersionPrefix("1.2.94"))
    }

    @Test
    fun `non-numeric components are treated as zero instead of throwing`() {
        assertFalse(isNewerVersion("1.2.93", "1.2.abc"))
        assertTrue(isNewerVersion("1.2.abc", "1.2.93"))
    }

    @Test
    fun `differing segment counts are compared with missing segments as zero`() {
        assertTrue(isNewerVersion("1.2.99", "1.3"))
        assertFalse(isNewerVersion("1.2.0", "1.2"))
    }
}
