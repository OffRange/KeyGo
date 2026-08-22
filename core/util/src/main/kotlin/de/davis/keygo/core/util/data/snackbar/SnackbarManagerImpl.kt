package de.davis.keygo.core.util.data.snackbar

import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.core.annotation.Single

@Single
internal class SnackbarManagerImpl : SnackbarManager {

    private val oneShotChannel = Channel<SnackbarMessage>(Channel.BUFFERED)

    override val oneShotEvents = oneShotChannel.receiveAsFlow()

    override fun sendMessage(message: SnackbarMessage) {
        oneShotChannel.trySend(message)
    }
}
