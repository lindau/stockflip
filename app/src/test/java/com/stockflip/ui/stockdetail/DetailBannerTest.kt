package com.stockflip.ui.stockdetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailBannerTest {
    @Test
    fun `ingen banderoll utan text`() {
        assertNull(detailBannerFor(null, null, true))
        assertNull(detailBannerFor(" ", "", false))
    }

    @Test
    fun `knappar bara för en utlöst bevakning som finns`() {
        assertEquals(true, detailBannerFor("Volvo B över 460 kr", "Målpris nått", true)!!.canAct)
        assertEquals(false, detailBannerFor("Volvo B över 460 kr", null, false)!!.canAct)
        assertEquals(false, detailBannerFor("Volvo B över 460 kr", null, null)!!.canAct)
    }
}
