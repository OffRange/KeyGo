package de.davis.keygo.core.security.presentation

import de.davis.keygo.core.security.data.SystemHandoffImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class HandoffLauncherTest {

    private val handoff = SystemHandoffImpl()

    @Test
    fun `launching arms a handoff, so the background it causes keeps the session`() {
        val launcher = HandoffLauncher<Unit>(handoff) {}

        launcher.launch(Unit)

        assertTrue(handoff.isPending)
    }

    @Test
    fun `the input reaches the launcher being wrapped`() {
        var launched: String? = null
        val launcher = HandoffLauncher<String>(handoff) { launched = it }

        launcher.launch("text/csv")

        assertEquals("text/csv", launched)
    }

    @Test
    fun `a system screen that will not open leaves no handoff armed behind it`() {
        val launcher = HandoffLauncher<Unit>(handoff) { error("nothing resolves this intent") }

        assertFailsWith<IllegalStateException> { launcher.launch(Unit) }

        assertFalse(handoff.isPending)
    }

    @Test
    fun `a system screen that will not open still reports the failure to the caller`() {
        val launcher = HandoffLauncher<Unit>(handoff) { error("nothing resolves this intent") }

        val thrown = assertFailsWith<IllegalStateException> { launcher.launch(Unit) }

        assertEquals("nothing resolves this intent", thrown.message)
    }
}
