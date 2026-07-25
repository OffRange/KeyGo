package de.davis.keygo.feature.settings.presentation.component

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import de.davis.keygo.core.util.presentation.UIText

@DslMarker
internal annotation class SettingsDsl

@SettingsDsl
internal class SettingsScope {
    private val sections = mutableListOf<SettingsSection>()

    fun section(@StringRes title: Int, block: SectionScope.() -> Unit) {
        sections += SettingsSection(title, SectionScope().apply(block).build())
    }

    fun build(): List<SettingsSection> = sections.toList()
}

@SettingsDsl
internal class SectionScope {
    private val entries = mutableListOf<SettingsEntry>()

    fun toggle(
        @StringRes title: Int,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        icon: ImageVector? = null,
        supporting: UIText? = null,
    ) {
        entries += SettingsEntry.Toggle(
            title = title,
            icon = icon,
            supporting = supporting,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }

    fun action(
        @StringRes title: Int,
        onClick: () -> Unit,
        icon: ImageVector? = null,
        navigationIcon: ImageVector? = null,
        supporting: UIText? = null,
    ) {
        entries += SettingsEntry.Action(
            title = title,
            icon = icon,
            supporting = supporting,
            navigationIcon = navigationIcon,
            onClick = onClick,
        )
    }

    fun value(
        @StringRes title: Int,
        value: String,
        icon: ImageVector? = null,
        supporting: UIText? = null,
        onClick: (() -> Unit)? = null,
    ) {
        entries += SettingsEntry.Value(
            title = title,
            icon = icon,
            supporting = supporting,
            value = value,
            onClick = onClick,
        )
    }

    fun build(): List<SettingsEntry> = entries.toList()
}
