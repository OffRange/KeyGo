package de.davis.keygo.core.domain.snackbar

import de.davis.keygo.core.domain.model.snackbar.SnackbarMessage
import kotlinx.coroutines.flow.Flow

interface SnackbarManager {

    val events: Flow<SnackbarMessage>

    suspend fun sendMessage(message: SnackbarMessage)
    suspend fun reset()
}