package de.davis.keygo.core.util.data.snackbar

import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.core.annotation.Single

@Single
internal class SnackbarManagerImpl : SnackbarManager {

    private val oneShotChannel = Channel<SnackbarMessage>(Channel.BUFFERED)
    private val _stickyMessage = MutableStateFlow<SnackbarMessage?>(null)

    override val oneShotEvents = oneShotChannel.receiveAsFlow()

    override val stickyMessage: StateFlow<SnackbarMessage?> = _stickyMessage.asStateFlow()

    override fun sendMessage(message: SnackbarMessage) {
        if (message.action != null) {
            _stickyMessage.value = message
        } else {
            oneShotChannel.trySend(message)
        }
    }

    override fun reset() {
        _stickyMessage.value = null
    }
}
