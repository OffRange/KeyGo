package de.davis.keygo.core.util.data.snackbar

import de.davis.keygo.core.util.domain.model.snackbar.SnackbarAction
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.presentation.UIText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `sendMessage emits one-shot message without action`() = runTest {
        val msg = message("hello")

        launch {
            manager.sendMessage(msg)
        }

        val received = manager.events.first()
        assertEquals(msg, received)
    }

    @Test
    fun `sendMessage emits sticky message with action`() = runTest {
        val msg = message("hello", withAction = true)

        launch {
            manager.sendMessage(msg)
        }

        val received = manager.events.first()
        assertEquals(msg, received)
    }

    @Test
    fun `sticky message is replayed to new collectors`() = runTest {
        val msg = message("sticky", withAction = true)
        manager.sendMessage(msg)

        // New collector should receive the replayed sticky message
        val received = manager.events.first()
        assertEquals(msg, received)
    }

    @Test
    fun `reset clears sticky replay cache`() = runTest {
        val msg = message("sticky", withAction = true)
        manager.sendMessage(msg)
        manager.reset()

        // After reset, send a new message to verify flow still works
        val newMsg = message("new", withAction = true)
        launch {
            manager.sendMessage(newMsg)
        }

        val received = manager.events.first()
        assertEquals(newMsg, received)
    }
}
