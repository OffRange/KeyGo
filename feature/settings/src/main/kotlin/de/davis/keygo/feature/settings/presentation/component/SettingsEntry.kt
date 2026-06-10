package de.davis.keygo.feature.settings.presentation.component

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

internal sealed interface SettingsEntry {
    @get:StringRes
    val title: Int
    val icon: ImageVector?

    @get:StringRes
    val supporting: Int?

    data class Toggle(
        @param:StringRes override val title: Int,
        override val icon: ImageVector? = null,
        @param:StringRes override val supporting: Int? = null,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : SettingsEntry

    data class Action(
        @param:StringRes override val title: Int,
        override val icon: ImageVector? = null,
        @param:StringRes override val supporting: Int? = null,
        val navigationIcon: ImageVector? = null,
        val onClick: () -> Unit,
    ) : SettingsEntry

    data class Value(
        @param:StringRes override val title: Int,
        override val icon: ImageVector? = null,
        @param:StringRes override val supporting: Int? = null,
        val value: String,
        val onClick: (() -> Unit)? = null,
    ) : SettingsEntry
}

internal data class SettingsSection(
    @param:StringRes val title: Int,
    val entries: List<SettingsEntry>,
)
