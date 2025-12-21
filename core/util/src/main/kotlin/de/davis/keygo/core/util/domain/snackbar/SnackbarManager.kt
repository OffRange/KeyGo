package de.davis.keygo.core.util.domain.snackbar

import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import kotlinx.coroutines.flow.Flow

interface SnackbarManager {

    val events: Flow<SnackbarMessage>

    suspend fun sendMessage(message: SnackbarMessage)
    suspend fun reset()
}