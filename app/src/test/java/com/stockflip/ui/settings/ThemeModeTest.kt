package com.stockflip.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `fromPref mappar AppCompat-lägen`() {
        assertEquals(ThemeMode.System, ThemeMode.fromPref(-1))
        assertEquals(ThemeMode.Light, ThemeMode.fromPref(1))
        assertEquals(ThemeMode.Dark, ThemeMode.fromPref(2))
    }

    @Test
    fun `okänt värde faller tillbaka på system`() {
        assertEquals(ThemeMode.System, ThemeMode.fromPref(42))
    }

    @Test
    fun `forcedDark är null för system`() {
        assertNull(ThemeMode.System.forcedDark)
        assertEquals(false, ThemeMode.Light.forcedDark)
        assertEquals(true, ThemeMode.Dark.forcedDark)
    }
}
