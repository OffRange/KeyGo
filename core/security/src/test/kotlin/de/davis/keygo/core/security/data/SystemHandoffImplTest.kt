package de.davis.keygo.core.security.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SystemHandoffImplTest {

    private val handoff = SystemHandoffImpl()

    @Test
    fun `nothing is pending before a handoff is armed`() {
        assertFalse(handoff.isPending)
    }

    @Test
    fun `expectReturn arms a handoff`() {
        handoff.expectReturn()

        assertTrue(handoff.isPending)
    }

    @Test
    fun `returned disarms the handoff`() {
        handoff.expectReturn()
        handoff.returned()

        assertFalse(handoff.isPending)
    }

    @Test
    fun `two armed handoffs need two returns`() {
        handoff.expectReturn()
        handoff.expectReturn()
        handoff.returned()

        assertTrue(handoff.isPending)
    }

    @Test
    fun `clear drops every armed handoff at once`() {
        handoff.expectReturn()
        handoff.expectReturn()
        handoff.clear()

        assertFalse(handoff.isPending)
    }

    @Test
    fun `a return that arrives after a clear does not eat the next handoff`() {
        handoff.expectReturn()
        handoff.clear()
        handoff.returned()

        handoff.expectReturn()

        assertTrue(handoff.isPending)
    }
}
