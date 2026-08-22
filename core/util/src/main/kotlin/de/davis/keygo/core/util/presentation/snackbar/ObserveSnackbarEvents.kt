package de.davis.keygo.core.util.presentation.snackbar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.core.util.presentation.resolve

@Composable
fun SnackbarHandler(
    snackbarHostState: SnackbarHostState,
    snackbarManager: SnackbarManager = LocalSnackbarManager.current
) {
    val context = LocalContext.current

    // Fire and forget, not replayed after rotation. Nothing may hang a pending write off a
    // snackbar's lifetime: a message that is dropped or replaced has to cost nothing but the
    // message.
    ObserveAsEvents(snackbarManager.oneShotEvents, snackbarManager) { msg ->
        snackbarHostState.showSnackbar(
            message = msg.message.resolve(context),
            duration = SnackbarDuration.Short,
        )
    }
}
