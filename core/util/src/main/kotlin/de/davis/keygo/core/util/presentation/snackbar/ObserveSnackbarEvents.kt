package de.davis.keygo.core.util.presentation.snackbar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.core.util.presentation.resolve
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun SnackbarHandler(
    snackbarHostState: SnackbarHostState,
    snackbarManager: SnackbarManager = LocalSnackbarManager.current
) {
    val context = LocalContext.current

    suspend fun show(msg: SnackbarMessage) {
        val text = msg.message.resolve(context)
        val actionText = msg.action?.label?.resolve(context)

        val result = snackbarHostState.showSnackbar(
            message = text,
            actionLabel = actionText,
            duration = SnackbarDuration.Short
        )

        when {
            msg.action != null && result == SnackbarResult.ActionPerformed -> {
                msg.action.onClick()
            }

            else -> {
                msg.onDismiss()
            }
        }
    }

    // One-shot messages (errors): fire and forget, not replayed after rotation
    ObserveAsEvents(snackbarManager.oneShotEvents, snackbarManager) { msg ->
        show(msg)
    }

    // Sticky messages (undo actions): replayed after rotation via StateFlow
    LaunchedEffect(snackbarManager) {
        snackbarManager.stickyMessage.filterNotNull().collect { msg ->
            show(msg)
            snackbarManager.reset()
        }
    }
}
