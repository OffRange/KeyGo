package de.davis.keygo.feature.settings.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.ListItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import de.davis.keygo.core.util.presentation.UIText

@DslMarker
internal annotation class SettingsDsl

@SettingsDsl
internal class SettingsScope {
    private val blocks = mutableListOf<SettingsBlock>()

    fun section(@StringRes title: Int, block: SectionScope.() -> Unit) {
        blocks += SettingsBlock.SettingsSection(title, SectionScope().apply(block).build())
    }

    fun custom(key: String, content: @Composable LazyItemScope.() -> Unit) {
        blocks += SettingsBlock.SettingsCustom(key, content)
    }

    fun build(): List<SettingsBlock> = blocks.toList()
}

@SettingsDsl
internal class SectionScope {
    private val entries = mutableListOf<SectionItem>()

    fun toggle(
        @StringRes title: Int,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        colors: ListItemColors,
        icon: ImageVector? = null,
        supporting: UIText? = null,
        enabled: Boolean = true,
    ) {
        entries += SettingsEntry.Toggle(
            title = title,
            icon = icon,
            supporting = supporting,
            colors = colors,
            checked = checked,
            enabled = enabled,
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

    fun custom(key: String, content: @Composable LazyItemScope.() -> Unit) {
        entries += SettingsBlock.SettingsCustom(key, content)
    }

    fun build(): List<SectionItem> = entries.toList()
}
