package de.davis.keygo.core.util.presentation.snackbar

import androidx.compose.runtime.staticCompositionLocalOf
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager

val LocalSnackbarManager = staticCompositionLocalOf<SnackbarManager> {
    error("No SnackbarManager provided")
}

