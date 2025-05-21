package de.davis.keygo.core.presentation

import androidx.compose.runtime.staticCompositionLocalOf

val LocalShowBackButton = staticCompositionLocalOf<Boolean> {
    error("No LocalShowBackButton provided")
}