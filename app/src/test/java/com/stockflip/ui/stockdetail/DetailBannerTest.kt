package com.stockflip.ui.stockdetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailBannerTest {
    @Test
    fun `ingen ruta utan text`() {
        assertNull(detailBannerFor(null, null))
        assertNull(detailBannerFor(" ", ""))
    }

    @Test
    fun `ruta med text`() {
        assertEquals("Insiderköp", detailBannerFor("Insiderköp", null)!!.title)
    }
}
