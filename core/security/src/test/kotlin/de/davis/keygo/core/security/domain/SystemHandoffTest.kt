package de.davis.keygo.core.security.domain

import de.davis.keygo.core.security.data.SystemHandoffImpl
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        val result = handoff.forRoundTrip { error("nothing resolves this intent") }

        assertTrue(result.isFailure())
        assertFalse(handoff.isPending)
    }

    @Test
    fun `a system screen that will not open still reports the failure to the caller`() {
        val result = handoff.forRoundTrip { error("nothing resolves this intent") }

        assertTrue(result.isFailure())
        assertEquals("nothing resolves this intent", (result as Result.Failure).error.message)
        assertNull(result.getOrNull())
    }

    @Test
    fun `a round trip that opens successfully reports success`() {
        val result = handoff.forRoundTrip { }

        assertNotNull(result.getOrNull())
    }
}
