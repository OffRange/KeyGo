package de.davis.keygo.core.presentation.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import de.davis.keygo.core.domain.navigation.Navigator

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided")
}