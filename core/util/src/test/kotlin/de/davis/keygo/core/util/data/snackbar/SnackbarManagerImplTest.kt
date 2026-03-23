package de.davis.keygo.core.util.data.snackbar

import de.davis.keygo.core.util.domain.model.snackbar.SnackbarAction
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.presentation.UIText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SnackbarManagerImplTest {

    private val manager = SnackbarManagerImpl()

    private fun message(text: String, withAction: Boolean = false) = SnackbarMessage(
        message = UIText.RawString(text),
        action = if (withAction) SnackbarAction(
            label = UIText.RawString("Undo"),
            onClick = {}
        ) else null
    )

    @Test
    fun `sendMessage buffers one-shot message without action`() = runTest {
        val msg = message("hello")

        manager.sendMessage(msg)

        val received = manager.oneShotEvents.first()
        assertEquals(msg, received)
    }

    @Test
    fun `sendMessage sets sticky message with action`() {
        val msg = message("hello", withAction = true)

        manager.sendMessage(msg)

        assertEquals(msg, manager.stickyMessage.value)
    }

    @Test
    fun `sticky message survives new collectors`() {
        val msg = message("sticky", withAction = true)
        manager.sendMessage(msg)

        // StateFlow replays to new collectors
        assertEquals(msg, manager.stickyMessage.value)
    }

    @Test
    fun `reset clears sticky message`() {
        val msg = message("sticky", withAction = true)
        manager.sendMessage(msg)
        manager.reset()

        assertNull(manager.stickyMessage.value)
    }

    @Test
    fun `reset does not affect one-shot messages`() = runTest {
        val msg = message("error")
        manager.sendMessage(msg)
        manager.reset()

        val received = manager.oneShotEvents.first()
        assertEquals(msg, received)
    }
}
