package de.davis.keygo.core.util.data.snackbar

import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import org.koin.core.annotation.Single

@Single
internal class SnackbarManagerImpl : SnackbarManager {

    private val stickyFlow = MutableSharedFlow<SnackbarMessage>(replay = 1, extraBufferCapacity = 1)
    private val oneShotFlow =
        MutableSharedFlow<SnackbarMessage>(replay = 0, extraBufferCapacity = 1)

    override val events: Flow<SnackbarMessage> = merge(stickyFlow, oneShotFlow)

    override suspend fun sendMessage(message: SnackbarMessage) {
        when (message.action) {
            null -> {
                oneShotFlow.emit(message)
            }

            else -> {
                stickyFlow.emit(message)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun reset() {
        stickyFlow.resetReplayCache()
    }
}