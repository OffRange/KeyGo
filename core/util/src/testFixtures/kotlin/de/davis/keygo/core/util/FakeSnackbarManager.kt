package de.davis.keygo.core.util

import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class FakeSnackbarManager : SnackbarManager {

    val messages: MutableList<SnackbarMessage> = mutableListOf()

    private val channel = Channel<SnackbarMessage>(Channel.UNLIMITED)

    override val oneShotEvents: Flow<SnackbarMessage> = channel.receiveAsFlow()

    override fun sendMessage(message: SnackbarMessage) {
        messages += message
        channel.trySend(message)
    }
}
