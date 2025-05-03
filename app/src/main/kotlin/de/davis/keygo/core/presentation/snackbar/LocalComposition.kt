package de.davis.keygo.core.presentation.snackbar

import androidx.compose.runtime.staticCompositionLocalOf
import de.davis.keygo.core.domain.snackbar.SnackbarManager

val LocalSnackbarManager = staticCompositionLocalOf<SnackbarManager> {
    error("No SnackbarManager provided")
}