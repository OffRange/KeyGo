package de.davis.keygo.feature.settings.presentation.component

import androidx.annotation.StringRes
import androidx.compose.material3.ListItemColors
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
        colors: ListItemColors,
        icon: ImageVector? = null,
        supporting: UIText? = null,
    ) {
        entries += SettingsEntry.Toggle(
            title = title,
            icon = icon,
            supporting = supporting,
            colors = colors,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }

    fun action(
        @StringRes title: Int,
        onClick: () -> Unit,
        colors: ListItemColors,
        icon: ImageVector? = null,
        navigationIcon: ImageVector? = null,
        supporting: UIText? = null,
    ) {
        entries += SettingsEntry.Action(
            title = title,
            icon = icon,
            supporting = supporting,
            colors = colors,
            navigationIcon = navigationIcon,
            onClick = onClick,
        )
    }

    fun value(
        @StringRes title: Int,
        value: String,
        colors: ListItemColors,
        icon: ImageVector? = null,
        supporting: UIText? = null,
        onClick: (() -> Unit)? = null,
    ) {
        entries += SettingsEntry.Value(
            title = title,
            icon = icon,
            supporting = supporting,
            colors = colors,
            value = value,
            onClick = onClick,
        )
    }

    fun <T> picker(
        @StringRes title: Int,
        selected: T,
        options: List<T>,
        label: (T) -> UIText,
        onSelect: (T) -> Unit,
        colors: ListItemColors,
        icon: ImageVector? = null,
    ) {
        entries += SettingsEntry.Picker(
            title = title,
            icon = icon,
            colors = colors,
            selectedIndex = options.indexOf(selected),
            options = options.map { option ->
                SettingsEntry.Picker.Option(label(option)) { onSelect(option) }
            },
        )
    }

    fun build(): List<SettingsEntry> = entries.toList()
}
