package de.davis.keygo.core.util.domain.snackbar

import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SnackbarManager {

    val oneShotEvents: Flow<SnackbarMessage>

    val stickyMessage: StateFlow<SnackbarMessage?>

    fun sendMessage(message: SnackbarMessage)
    fun reset()
}
