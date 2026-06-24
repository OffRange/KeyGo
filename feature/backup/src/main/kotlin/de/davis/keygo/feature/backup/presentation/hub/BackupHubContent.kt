package de.davis.keygo.feature.backup.presentation.hub

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.displayName
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.model.IntervalUnit
import de.davis.keygo.feature.backup.presentation.displayName
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubUiEvent
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubUiState
import de.davis.keygo.feature.backup.presentation.hub.model.ScheduledBackup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BackupHubContent(state: BackupHubUiState, onEvent: (BackupHubUiEvent) -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ListItem(
                onClick = {},
                // Workaround to disable clicking but remaining original color schema
                colors = ListItemDefaults.colors(
                    disabledContainerColor = ListItemDefaults.colors().containerColor,
                    disabledContentColor = ListItemDefaults.colors().contentColor,
                    disabledOverlineContentColor = ListItemDefaults.colors().overlineContentColor,
                    disabledLeadingContentColor = ListItemDefaults.colors().leadingContentColor,
                    disabledSupportingContentColor = ListItemDefaults.colors().supportingContentColor,
                ),
                enabled = false,
                overlineContent = {
                    Text(text = stringResource(R.string.last_backup))
                },
                leadingContent = {
                    Box(modifier = Modifier.wrapContentHeight()) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                        )
                    }
                },
                supportingContent = state.lastBackupProvider?.let { { Text(text = it) } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = state.lastBackupAt ?: stringResource(R.string.never_backed_up))
            }

            FilledTonalButton(
                onClick = { onEvent(BackupHubUiEvent.OnRestoreBackup) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.SettingsBackupRestore,
                    modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(R.string.restore_backup),
                )
            }

            FilledTonalButton(
                onClick = { onEvent(BackupHubUiEvent.OnScheduleBackupClick) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(R.string.schedule_new_backup),
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.scheduled_backups),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Surface(
                modifier = Modifier.weight(1f),
            ) {
                AnimatedContent(state.hasScheduledItems) { hasItems ->
                    when {
                        hasItems -> LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(items = state.scheduledBackups) { idx, item ->
                                SegmentedListItem(
                                    onClick = {},
                                    shapes = ListItemDefaults.segmentedShapes(
                                        idx,
                                        state.scheduledBackups.size
                                    ),
                                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    overlineContent = {
                                        Text(text = item.type.displayName)
                                    },
                                    supportingContent = {
                                        Text(text = item.path)
                                    },
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.scheduled_title,
                                            item.provider,
                                            item.scheduleInterval.displayName
                                        )
                                    )
                                }
                            }
                        }

                        else -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.no_scheduled_backups),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Preview
@Composable
private fun BackupHubContentPreview() {
    val currentTime = {
        val zonedDateTime = Instant.now().atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
        zonedDateTime.format(formatter)
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            BackupHubContent(
                state = BackupHubUiState(
                    lastBackupAt = currentTime(),
                    lastBackupProvider = "Nextcloud",
                    scheduledBackups = listOf(
                        ScheduledBackup(
                            provider = "Nextcloud",
                            type = FileFormat.CSV,
                            scheduleInterval = BackupInterval(count = 1, unit = IntervalUnit.Weeks),
                            path = "/path/to/backup"
                        ),
                        ScheduledBackup(
                            provider = "Google Drive",
                            type = FileFormat.JSON,
                            scheduleInterval = BackupInterval(count = 1, unit = IntervalUnit.Days),
                            path = "/path/to/drive/backup"
                        )
                    )
                ),
                onEvent = {},
            )
        }
    }
}