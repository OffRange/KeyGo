package de.davis.keygo.core.presentation

import androidx.compose.runtime.staticCompositionLocalOf

val LocalIsInSinglePaneMode = staticCompositionLocalOf<Boolean> {
    error("No LocalIsInSinglePaneMode provided")
}