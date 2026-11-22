package de.davis.keygo.core.ui.components.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.ListItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import de.davis.keygo.core.util.presentation.UIText

sealed interface SectionItem

sealed interface SettingsEntry : SectionItem {
    @get:StringRes
    val title: Int
    val icon: ImageVector?

    /** [UIText] rather than a string resource: some rows describe live state. */
    val supporting: UIText?

    /** Null falls back to [SettingsListDefaults.colors] when the row is drawn. */
    val colors: ListItemColors?

    data class Toggle(
        @param:StringRes override val title: Int,
        override val icon: ImageVector? = null,
        override val supporting: UIText? = null,
        override val colors: ListItemColors? = null,
        val checked: Boolean,
        val enabled: Boolean = true,
        val onCheckedChange: (Boolean) -> Unit,
    ) : SettingsEntry

    data class Action(
        @param:StringRes override val title: Int,
        override val icon: ImageVector? = null,
        override val supporting: UIText? = null,
        override val colors: ListItemColors? = null,
        val navigationIcon: ImageVector? = null,
        val onClick: () -> Unit,
    ) : SettingsEntry

    data class Value(
        @param:StringRes override val title: Int,
        override val icon: ImageVector? = null,
        override val supporting: UIText? = null,
        override val colors: ListItemColors? = null,
        val value: String,
        val onClick: (() -> Unit)? = null,
    ) : SettingsEntry

    data class Picker(
        @param:StringRes override val title: Int,
        override val icon: ImageVector? = null,
        override val colors: ListItemColors? = null,
        val selectedIndex: Int,
        val options: List<Option>,
    ) : SettingsEntry {

        override val supporting: UIText? = options.getOrNull(selectedIndex)?.label

        data class Option(val label: UIText, val onSelect: () -> Unit)
    }
}

sealed interface SettingsBlock {
    data class SettingsSection(
        val key: String,
        @param:StringRes val title: Int?,
        val entries: List<SectionItem>,
    ) : SettingsBlock

    data class SettingsCustom(
        val key: String,
        val content: @Composable LazyItemScope.() -> Unit,
    ) : SettingsBlock, SectionItem
}
