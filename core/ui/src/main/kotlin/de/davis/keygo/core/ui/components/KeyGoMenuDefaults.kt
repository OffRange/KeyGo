package de.davis.keygo.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableMenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object KeyGoMenuDefaults {

    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest

    /** Item colors matching [containerColor]; the M3 defaults paint items `surfaceContainerLow`. */
    val selectableItemColors: SelectableMenuItemColors
        @Composable
        get() = MenuDefaults.selectableItemColors(containerColor = containerColor)
}
