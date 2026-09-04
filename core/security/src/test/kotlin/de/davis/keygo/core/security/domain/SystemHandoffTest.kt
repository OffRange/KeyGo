package de.davis.keygo.core.security.domain

import de.davis.keygo.core.security.data.SystemHandoffImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SystemHandoffTest {

    private val handoff = SystemHandoffImpl()

    @Test
    fun `a round trip stays armed after the system screen has been opened`() {
        handoff.forRoundTrip { }

        assertTrue(handoff.isPending)
    }

    @Test
    fun `a system screen that will not open leaves no handoff armed behind it`() {
        assertFailsWith<IllegalStateException> {
            handoff.forRoundTrip { error("nothing resolves this intent") }
        }

        assertFalse(handoff.isPending)
    }

    @Test
    fun `a system screen that will not open still reports the failure to the caller`() {
        val thrown = assertFailsWith<IllegalStateException> {
            handoff.forRoundTrip { error("nothing resolves this intent") }
        }

        assertEquals("nothing resolves this intent", thrown.message)
    }
}
