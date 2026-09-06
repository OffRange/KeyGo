package de.davis.keygo.feature.settings.presentation.component

import androidx.annotation.StringRes
import androidx.compose.material3.ListItemColors
import androidx.compose.ui.graphics.vector.ImageVector
import de.davis.keygo.core.util.presentation.UIText

internal sealed interface SettingsEntry {
    @get:StringRes
    val title: Int
    val icon: ImageVector?

    /** [UIText] rather than a string resource: some rows describe live state. */
    val supporting: UIText?

    val colors: ListItemColors

    data class Toggle(
        @param:StringRes override val title: Int,
        override val icon: ImageVector? = null,
        override val supporting: UIText? = null,
        override val colors: ListItemColors,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : SettingsEntry

    data class Action(
        @param:StringRes override val title: Int,
        override val icon: ImageVector? = null,
        override val supporting: UIText? = null,
        override val colors: ListItemColors,
        val navigationIcon: ImageVector? = null,
        val onClick: () -> Unit,
    ) : SettingsEntry

    data class Value(
        @param:StringRes override val title: Int,
        override val icon: ImageVector? = null,
        override val supporting: UIText? = null,
        override val colors: ListItemColors,
        val value: String,
        val onClick: (() -> Unit)? = null,
    ) : SettingsEntry

    data class Picker(
        @param:StringRes override val title: Int,
        override val icon: ImageVector? = null,
        override val colors: ListItemColors,
        val selectedIndex: Int,
        val options: List<Option>,
    ) : SettingsEntry {

        override val supporting: UIText? = options.getOrNull(selectedIndex)?.label

        data class Option(val label: UIText, val onSelect: () -> Unit)
    }
}

internal data class SettingsSection(
    @param:StringRes val title: Int,
    val entries: List<SettingsEntry>,
)
