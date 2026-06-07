package de.davis.keygo.feature.settings.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: SettingsScope.() -> Unit,
) {
    val sections = SettingsScope().apply(content).build()

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        sections.forEach { section ->
            stickyHeader(key = "header_${section.title}") {
                SettingsSectionHeader(title = section.title)
            }

            itemsIndexed(
                items = section.entries,
                key = { index, entry -> "${section.title}_${index}_${entry.title}" },
            ) { index, entry ->
                SettingsEntryRow(
                    entry = entry,
                    shapes = ListItemDefaults.segmentedShapes(index, section.entries.size),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsEntryRow(
    entry: SettingsEntry,
    shapes: ListItemShapes,
    colors: ListItemColors,
    verticalAlignment: Alignment.Vertical
) {
    val leadingContent: (@Composable () -> Unit)? = entry.icon?.let { icon ->
        {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                    )
                }
            }
        }
    }
    val supportingContent: (@Composable () -> Unit)? = entry.supporting?.let { res ->
        { Text(text = stringResource(res)) }
    }
    val headlineContent: @Composable () -> Unit = { Text(text = stringResource(entry.title)) }

    when (entry) {
        is SettingsEntry.Toggle -> SegmentedListItem(
            onClick = { entry.onCheckedChange(!entry.checked) },
            shapes = shapes,
            colors = colors,
            leadingContent = leadingContent,
            supportingContent = supportingContent,
            verticalAlignment = verticalAlignment,
            trailingContent = {
                Switch(
                    checked = entry.checked,
                    onCheckedChange = null,
                    thumbContent = {
                        Icon(
                            imageVector = when {
                                entry.checked -> Icons.Default.Check
                                else -> Icons.Default.Close
                            },
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                )
            },
            content = headlineContent,
        )

        is SettingsEntry.Action -> SegmentedListItem(
            onClick = entry.onClick,
            shapes = shapes,
            colors = colors,
            leadingContent = leadingContent,
            supportingContent = supportingContent,
            verticalAlignment = verticalAlignment,
            trailingContent = if (entry.isNavigation) {
                {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                }
            } else null,
            content = headlineContent,
        )

        is SettingsEntry.Value -> SegmentedListItem(
            onClick = entry.onClick ?: {},
            shapes = shapes,
            colors = colors,
            leadingContent = leadingContent,
            supportingContent = supportingContent,
            verticalAlignment = verticalAlignment,
            trailingContent = { Text(text = entry.value) },
            content = headlineContent,
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}
