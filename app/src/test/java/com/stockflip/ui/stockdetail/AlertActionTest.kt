package com.stockflip.ui.stockdetail

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertActionTest {
    @Test
    fun `utlöst engångsbevakning är inaktiv men ska kunna återaktiveras`() {
        assertEquals(AlertAction.Reactivate, alertActionFor(triggered = true, isActive = false))
        assertEquals("Utlöst", alertStatusLabel(triggered = true, isActive = false))
    }

    @Test
    fun `pausad kan aktiveras och aktiv kan pausas`() {
        assertEquals(AlertAction.Resume, alertActionFor(triggered = false, isActive = false))
        assertEquals("Pausad", alertStatusLabel(triggered = false, isActive = false))
        assertEquals(AlertAction.Pause, alertActionFor(triggered = false, isActive = true))
        assertEquals("Väntar", alertStatusLabel(triggered = false, isActive = true))
    }

    @Test
    fun `utlöst och aktiv (återkommande) återaktiveras`() {
        assertEquals(AlertAction.Reactivate, alertActionFor(triggered = true, isActive = true))
    }
}
