package de.davis.keygo.core.util.presentation.snackbar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.core.util.presentation.resolve

@Composable
fun SnackbarHandler(
    snackbarManager: SnackbarManager = LocalSnackbarManager.current,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    ObserveAsEvents(snackbarManager.events, snackbarManager) { msg ->
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

        if (msg.action != null)
            snackbarManager.reset()
    }
}