package de.davis.keygo.core.util.data.snackbar

import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.presentation.UIText
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SnackbarManagerImplTest {

    private val manager = SnackbarManagerImpl()

    private fun message(text: String) = SnackbarMessage(message = UIText.RawString(text))

    @Test
    fun `sendMessage buffers a message`() = runTest {
        val msg = message("hello")

        manager.sendMessage(msg)

        assertEquals(listOf(msg), manager.oneShotEvents.take(1).toList())
    }

    @Test
    fun `messages sent back to back are all delivered in order`() = runTest {
        val first = message("first")
        val second = message("second")
        val third = message("third")

        manager.sendMessage(first)
        manager.sendMessage(second)
        manager.sendMessage(third)

        assertEquals(
            listOf(first, second, third),
            manager.oneShotEvents.take(3).toList(),
        )
    }
}
